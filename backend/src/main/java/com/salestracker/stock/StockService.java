package com.salestracker.stock;

import com.salestracker.auth.ApiException;
import com.salestracker.common.PageResponse;
import com.salestracker.common.Search;
import com.salestracker.order.OrderItem;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import com.salestracker.stock.StockDtos.AdjustRequest;
import com.salestracker.stock.StockDtos.AdjustmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock is only tracked for products that have a stock quantity; a product without one is left alone everywhere.
 * Every change to a tracked product's stock goes through here so it is always written to the log.
 */
@Service
@Transactional
public class StockService {
    private final StockAdjustmentRepository adjustments;
    private final ProductRepository products;

    public StockService(StockAdjustmentRepository adjustments, ProductRepository products) {
        this.adjustments = adjustments;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdjustmentResponse> list(Long tenantId, Long productId, StockReason reason, int page, int size) {
        Collection<StockReason> reasons = reason == null ? List.of(StockReason.values()) : List.of(reason);
        Pageable newestFirst = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<StockAdjustment> result = adjustments.search(tenantId, productId == null ? 0L : productId, reasons, newestFirst);

        Map<Long, Product> productById = products.findAllById(
                        result.stream().map(StockAdjustment::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return PageResponse.of(result, a -> new AdjustmentResponse(a.getId(), a.getCreatedAt(), a.getProductId(),
                productById.get(a.getProductId()).getName(), a.getReason(), a.getQuantityChange(), a.getStockAfter(),
                a.getOrderId() != null ? orderNote(a.getQuantityChange(), a.getOrderId()) : a.getNote(),
                a.getOrderId()));
    }

    /** Built from the order id when shown, not taken from the stored text, so it always names the order it links to. */
    private static String orderNote(int change, Long orderId) {
        return (change < 0 ? "Sold on order #" : "Returned from order #") + orderId;
    }

    /** A manual adjustment (restock, damaged goods, count correction). Stock can never be taken below zero. */
    public AdjustmentResponse adjust(Long tenantId, AdjustRequest req) {
        int change = req.change();
        if (change == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The change must not be zero");
        }
        switch (req.reason()) {
            case RESTOCK -> { if (change < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "A restock must add units"); }
            case DAMAGE -> { if (change > 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Damaged stock must remove units"); }
            case CORRECTION -> { }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Reason must be RESTOCK, DAMAGE or CORRECTION");
        }
        Product p = products.lockByIdAndTenantId(req.productId(), tenantId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        if (p.getStockQty() == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This product does not track stock. Set a stock quantity on the product first.");
        }
        long after = (long) p.getStockQty() + change;
        if (after < 0 || after > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only " + p.getStockQty() + " in stock; that adjustment would take it out of range");
        }
        p.addStock(change);
        StockAdjustment a = adjustments.save(new StockAdjustment(tenantId, p.getId(), null, req.reason(), change,
                p.getStockQty(), Search.blankToNull(req.note())));
        return new AdjustmentResponse(a.getId(), a.getCreatedAt(), p.getId(), p.getName(), a.getReason(),
                a.getQuantityChange(), a.getStockAfter(), a.getNote(), null);
    }

    /**
     * Deletes a manual adjustment (restock, damage, correction) and reverses its effect on the product's stock.
     * Sale rows belong to their order - edit, return or cancel the order instead - and opening stock is set on the
     * product. Later rows keep their "stock after" as it was at the time.
     */
    public void delete(Long tenantId, Long id) {
        StockAdjustment a = adjustments.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stock log entry not found"));
        switch (a.getReason()) {
            case RESTOCK, DAMAGE, CORRECTION -> { }
            case ORDER -> throw new ApiException(HttpStatus.CONFLICT,
                    "This entry comes from an order. Edit, return or cancel the order to change it.");
            default -> throw new ApiException(HttpStatus.CONFLICT,
                    "Opening stock is set on the product. Edit the product's stock quantity instead.");
        }
        Product p = products.lockByIdAndTenantId(a.getProductId(), tenantId).orElse(null);
        if (p != null && p.getStockQty() != null) {
            int reverse = -a.getQuantityChange();
            if ((long) p.getStockQty() + reverse < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Only " + p.getStockQty()
                        + " in stock; undoing this entry would take it below zero");
            }
            p.addStock(reverse);
        }
        adjustments.delete(a);
    }

    /**
     * Logs a stock quantity typed into the product form. {@code before} is the stock the product had before the edit
     * (null = it did not track stock). Turning tracking off writes nothing.
     */
    public void recordProductEdit(Product p, Integer before) {
        Integer after = p.getStockQty();
        if (after == null || after.equals(before)) return;
        int prior = before == null ? 0 : before;
        adjustments.save(new StockAdjustment(p.getTenantId(), p.getId(), null,
                before == null ? StockReason.INITIAL : StockReason.CORRECTION, after - prior, after,
                before == null ? "Opening stock" : "Edited on the product"));
    }

    /**
     * Keeps stock in step with an order (call after every create, edit or status change). While the order stands
     * (PAID or PENDING) it holds its quantities; when it is returned or cancelled the units go back. Only the
     * difference from what the order has already taken is written, so calling this again is harmless.
     */
    public void syncOrder(SaleOrder order) {
        boolean holds = order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PENDING;
        Map<Long, Integer> wanted = new HashMap<>();
        if (holds) {
            for (OrderItem i : order.getItems()) wanted.merge(i.getProductId(), i.getQuantity(), Integer::sum);
        }
        apply(order, wanted);
    }

    /** Gives back everything the order took; call before deleting it. */
    public void releaseOrder(SaleOrder order) {
        apply(order, Map.of());
    }

    private void apply(SaleOrder order, Map<Long, Integer> wanted) {
        Long tenantId = order.getTenantId();
        Map<Long, Integer> held = new HashMap<>();
        for (Object[] row : adjustments.netByProductForOrder(tenantId, order.getId())) {
            held.put((Long) row[0], -((Number) row[1]).intValue());
        }

        // Ascending id order keeps concurrent orders from locking the same products in opposite orders.
        SortedSet<Long> productIds = new TreeSet<>(wanted.keySet());
        productIds.addAll(held.keySet());
        for (Long productId : productIds) {
            int change = held.getOrDefault(productId, 0) - wanted.getOrDefault(productId, 0);
            if (change == 0) continue;
            Product p = products.lockByIdAndTenantId(productId, tenantId).orElse(null);
            if (p == null || p.getStockQty() == null) continue; // not tracking stock for this product
            // Selling past zero is allowed: the sale already happened, the log just shows the shortfall.
            p.addStock(change);
            adjustments.save(new StockAdjustment(tenantId, productId, order.getId(), StockReason.ORDER, change,
                    p.getStockQty(), orderNote(change, order.getId())));
        }
    }
}
