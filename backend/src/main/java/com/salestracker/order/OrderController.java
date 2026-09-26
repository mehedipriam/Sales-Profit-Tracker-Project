package com.salestracker.order;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.DateRange;
import com.salestracker.common.PageResponse;
import com.salestracker.order.OrderDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal AuthUser user,
                                           @RequestParam(required = false) Long platformId,
                                           @RequestParam(required = false) OrderStatus status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        PageResponse<OrderSummary> result = service.list(user.tenantId(), platformId, status, DateRange.of(from, to), page, size);
        return seesFinancials(user) ? result : result.map(OrderSummary::hideFinancials);
    }

    @GetMapping("/{id}")
    public OrderDetail get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        OrderDetail result = service.get(user.tenantId(), id);
        return seesFinancials(user) ? result : result.hideFinancials();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody OrderRequest req) {
        OrderDetail result = service.create(user.tenantId(), req);
        return seesFinancials(user) ? result : result.hideFinancials();
    }

    @PutMapping("/{id}")
    public OrderDetail update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @Valid @RequestBody OrderRequest req) {
        OrderDetail result = service.update(user.tenantId(), id, req);
        return seesFinancials(user) ? result : result.hideFinancials();
    }

    @PatchMapping("/{id}/status")
    public OrderDetail changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody StatusRequest req) {
        OrderDetail result = service.changeStatus(user.tenantId(), id, req.status());
        return seesFinancials(user) ? result : result.hideFinancials();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.tenantId(), id);
    }

    /** Phase 7b: cost/profit figures are Owner/Admin-only; Staff still records and manages the sale itself. */
    private static boolean seesFinancials(AuthUser user) {
        return user.role().seesFinancials();
    }
}
