package com.salestracker.customer;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.PageResponse;
import com.salestracker.customer.CustomerDtos.CustomerRequest;
import com.salestracker.customer.CustomerDtos.CustomerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long platformId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(user.tenantId(), q, platformId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CustomerRequest req) {
        return service.create(user.tenantId(), req);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest req) {
        return service.update(user.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.tenantId(), id);
    }
}
