package com.salestracker.agent;

import com.salestracker.agent.DraftOrder.MatchStatus;
import com.salestracker.agent.Intent.StockDetails;
import com.salestracker.agent.LedgerReplies.StockCard;
import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import com.salestracker.stock.StockDtos.AdjustRequest;
import com.salestracker.stock.StockDtos.AdjustmentResponse;
import com.salestracker.stock.StockReason;
import com.salestracker.stock.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Restocking, damaged goods and count corrections from the chat, for everyone who can use the Stock page. The message
 * only prepares a card with the stock before and after; Confirm goes through the same stock log as the Stock page.
 */
@Service
public class StockAssistant {
    private final ProductRepository products;
    private final StockService stock;

    public StockAssistant(ProductRepository products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public AgentReply handle(AuthUser user, Intent in) {
        StockDetails d = in.stock();
        if (d == null || d.reason() == null) {
            return AgentReply.message("Is it a restock, damaged goods, or a correction of the count?");
        }
        if (d.reason() == StockReason.CORRECTION ? d.newStock() == null && d.quantity() == null
                                                  : d.quantity() == null || d.quantity() < 1) {
            return AgentReply.message(d.reason() == StockReason.CORRECTION
                    ? "What is the real stock count?" : "How many units? It must be at least 1.");
        }
        if (d.product() == null || d.product().isBlank()) return AgentReply.message("Which product?");

        List<Product> catalog = products.findByTenantIdAndActiveTrue(user.tenantId());
        ProductMatcher.Match m = ProductMatcher.match(d.product(), catalog);
        if (m.status() == MatchStatus.NOT_FOUND) {
            String near = m.candidates().isEmpty() ? "" : " Did you mean "
                    + m.candidates().stream().map(Product::getName).collect(Collectors.joining(", ")) + "?";
            return AgentReply.message("No product matches \"" + d.product().trim() + "\"." + near);
        }
        if (m.status() == MatchStatus.AMBIGUOUS) {
            return AgentReply.message("Which product did you mean? "
                    + m.candidates().stream().map(Product::getName).collect(Collectors.joining(", ")));
        }
        Product p = m.product();
        if (p.getStockQty() == null) {
            return AgentReply.message(p.getName() + " does not track stock. Set a stock quantity on the product first.");
        }

        int before = p.getStockQty();
        long change = switch (d.reason()) {
            case RESTOCK -> d.quantity();
            case DAMAGE -> -(long) d.quantity();
            default -> d.newStock() != null ? (long) d.newStock() - before : d.quantity();
        };
        if (d.reason() == StockReason.CORRECTION && d.newStock() != null && d.newStock() < 0) {
            return AgentReply.message("The stock cannot be below zero.");
        }
        if (change == 0) return AgentReply.message(p.getName() + " already has " + before + " in stock. Nothing to change.");
        long after = before + change;
        if (after < 0) {
            return AgentReply.message("Only " + before + " of " + p.getName() + " in stock, so " + (-change)
                    + " cannot be taken out. Nothing was changed.");
        }
        if (after > Integer.MAX_VALUE) return AgentReply.message("That quantity is too large.");
        return AgentReply.stock(new StockCard(p.getId(), p.getName(), (int) change, d.reason(), before, (int) after));
    }

    /** Confirm: refuses if the stock has moved since the card was shown, then logs the change like the Stock page. */
    @Transactional
    public AdjustmentResponse confirm(AuthUser user, Long productId, int change, StockReason reason, int expectedBefore) {
        Product p = products.findByIdAndTenantIdAndActiveTrue(productId, user.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        if (p.getStockQty() != null && p.getStockQty() != expectedBefore) {
            throw new ApiException(HttpStatus.CONFLICT, "Stock of " + p.getName() + " is now " + p.getStockQty()
                    + ", not " + expectedBefore + ". Ask again to see the current stock");
        }
        return stock.adjust(user.tenantId(), new AdjustRequest(productId, change, reason, "Entered in the assistant"));
    }
}
