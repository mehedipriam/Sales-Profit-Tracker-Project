package com.salestracker.expense;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.DateRange;
import com.salestracker.common.PageResponse;
import com.salestracker.expense.ExpenseDtos.ExpenseRequest;
import com.salestracker.expense.ExpenseDtos.ExpenseResponse;
import com.salestracker.expense.ExpenseDtos.ExpenseSummary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Owner only (Phase 7b): expenses are financial detail, same bracket as Dashboard/Reports. */
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    /** Newest first. from/to are inclusive expense dates (yyyy-MM-dd). */
    @GetMapping
    public PageResponse<ExpenseResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) ExpenseType type,
                                              @RequestParam(required = false) Long orderId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(user.tenantId(), type, orderId, DateRange.of(from, to), page, size);
    }

    /** Total and per-type breakdown for the date range (all types, whatever the list is filtered to). */
    @GetMapping("/summary")
    public ExpenseSummary summary(@AuthenticationPrincipal AuthUser user,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(user.tenantId(), DateRange.of(from, to));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ExpenseRequest req) {
        return service.create(user.tenantId(), req);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody ExpenseRequest req) {
        return service.update(user.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.tenantId(), id);
    }
}
