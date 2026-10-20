package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.customer.CustomerDtos.CustomerPatch;
import com.salestracker.customer.CustomerDtos.CustomerRequest;
import com.salestracker.customer.CustomerDtos.CustomerResponse;
import com.salestracker.order.OrderDtos.OrderDetail;
import com.salestracker.order.OrderDtos.OrderRequest;
import jakarta.validation.Valid;
import com.salestracker.expense.ExpenseDtos.ExpenseRequest;
import com.salestracker.expense.ExpenseDtos.ExpenseResponse;
import com.salestracker.order.OrderStatus;
import com.salestracker.stock.StockDtos.AdjustmentResponse;
import com.salestracker.stock.StockReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The order agent. /draft reads a pasted message and prepares an order without saving anything; /confirm saves it once
 * a person has checked it. Anyone who can record an order can use them, with the same limits as the order form.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    public record TextRequest(@NotBlank @Size(max = 4000) String text) {}

    private final AssistantService assistant;
    private final CustomerAssistant customerAssistant;
    private final OrderStatusAssistant orderStatus;
    private final ExpenseAssistant expenseAssistant;
    private final StockAssistant stockAssistant;
    private final DraftService drafts;
    private final RevisionService revisions;
    private final AgentRateLimiter limiter;

    public AgentController(DraftService drafts, RevisionService revisions, AgentRateLimiter limiter, AssistantService assistant,
                           CustomerAssistant customerAssistant, OrderStatusAssistant orderStatus, ExpenseAssistant expenseAssistant,
                           StockAssistant stockAssistant) {
        this.expenseAssistant = expenseAssistant;
        this.stockAssistant = stockAssistant;
        this.orderStatus = orderStatus;
        this.customerAssistant = customerAssistant;
        this.assistant = assistant;
        this.drafts = drafts;
        this.revisions = revisions;
        this.limiter = limiter;
    }

    /**
     * A typed message of any kind: an order to draft, or a question about the business. Answers with a draft, an answer
     * card or a plain message. Questions change nothing; money questions are for Owner and Admin.
     */
    @PostMapping("/message")
    public AgentReply message(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TextRequest req) {
        limiter.check(user.userId());
        return assistant.handle(user, req.text());
    }

    @PostMapping("/draft")
    public DraftOrder draft(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TextRequest req) {
        limiter.check(user.userId());
        return drafts.prepare(user, req.text());
    }

    /** A screenshot (JPEG, PNG or WebP) instead of pasted text, with an optional note beside it. */
    @PostMapping(value = "/draft/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftOrder draftFromImage(@AuthenticationPrincipal AuthUser user,
                                     @RequestPart("image") MultipartFile image,
                                     @RequestParam(value = "text", required = false) String text) {
        if (text != null && text.length() > 4000) throw new ApiException(HttpStatus.BAD_REQUEST, "The note is too long");
        limiter.check(user.userId());
        return drafts.prepare(user, text, ImageUpload.read(image));
    }

    /** Confirm on an "add customer" card. Anyone who can use the Customers page can; a phone number already on file is refused. */
    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse addCustomer(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CustomerRequest req) {
        return customerAssistant.confirmAdd(user, req);
    }

    /** Confirm on an "update customer" card: only the fields sent change. */
    @PatchMapping("/customers/{id}")
    public CustomerResponse updateCustomer(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                           @Valid @RequestBody CustomerPatch patch) {
        return customerAssistant.confirmUpdate(user, id, patch);
    }

    public record StockConfirm(@NotNull Long productId, @NotNull Integer change, @NotNull StockReason reason,
                               @NotNull Integer before) {}

    /** Confirm on an expense card: saved like an expense typed on the Expenses page (Owner and Admin only). */
    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse addExpense(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ExpenseRequest req) {
        return expenseAssistant.confirm(user, req);
    }

    /** Confirm on a stock card: logged like the Stock page, refused if the stock moved since the card was shown. */
    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse adjustStock(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody StockConfirm req) {
        return stockAssistant.confirm(user, req.productId(), req.change(), req.reason(), req.before());
    }

    public record StatusConfirm(@NotNull OrderStatus from, @NotNull OrderStatus to) {}

    /**
     * Confirm on a status card. Uses the app's own status change (stock, commission and the Activity log follow), and
     * refuses if the order is no longer in the status the card showed.
     */
    @PostMapping("/orders/{id}/status")
    public OrderDetail changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody StatusConfirm req) {
        OrderDetail changed = orderStatus.confirm(user, id, req.from(), req.to());
        return user.role().seesFinancials() ? changed : changed.hideFinancials();
    }

    /** "Make it 3", "remove the red shirt": changes an open draft as asked and checks it again. Saves nothing. */
    @PostMapping("/revise")
    public RevisionService.Revised revise(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody RevisionService.RevisionRequest req) {
        limiter.check(user.userId());
        return revisions.revise(user, req);
    }

    /** The (possibly corrected) draft as an ordinary order request: a customerId, or newCustomer to create one. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail confirm(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody OrderRequest req) {
        OrderDetail created = drafts.confirm(user, req);
        return user.role().seesFinancials() ? created : created.hideFinancials();
    }
}
