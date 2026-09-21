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
        return service.list(user.tenantId(), platformId, status, DateRange.of(from, to), page, size);
    }

    @GetMapping("/{id}")
    public OrderDetail get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user.tenantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody OrderRequest req) {
        return service.create(user.tenantId(), req);
    }

    @PutMapping("/{id}")
    public OrderDetail update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @Valid @RequestBody OrderRequest req) {
        return service.update(user.tenantId(), id, req);
    }

    @PatchMapping("/{id}/status")
    public OrderDetail changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(user.tenantId(), id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.tenantId(), id);
    }
}
