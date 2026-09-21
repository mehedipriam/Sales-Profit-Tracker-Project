package com.salestracker.customer;

import com.salestracker.auth.ApiException;
import com.salestracker.common.PageResponse;
import com.salestracker.common.Search;
import com.salestracker.customer.CustomerDtos.CustomerRequest;
import com.salestracker.customer.CustomerDtos.CustomerResponse;
import com.salestracker.platform.PlatformRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {
    private final CustomerRepository customers;
    private final PlatformRepository platforms;

    public CustomerService(CustomerRepository customers, PlatformRepository platforms) {
        this.customers = customers;
        this.platforms = platforms;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Long tenantId, String q, Long platformId, int page, int size) {
        return PageResponse.of(
                customers.search(tenantId, Search.pattern(q), platformId == null ? 0L : platformId,
                        Search.page(page, size, "name")),
                CustomerResponse::of);
    }

    public CustomerResponse create(Long tenantId, CustomerRequest req) {
        return save(new Customer(tenantId), req);
    }

    public CustomerResponse update(Long tenantId, Long id, CustomerRequest req) {
        return save(find(tenantId, id), req);
    }

    public void delete(Long tenantId, Long id) {
        Customer c = find(tenantId, id);
        try {
            customers.delete(c);
            customers.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This customer has orders and cannot be deleted");
        }
    }

    private Customer find(Long tenantId, Long id) {
        return customers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CustomerResponse save(Customer c, CustomerRequest req) {
        Long platformId = req.sourcePlatformId();
        if (platformId != null && !platforms.existsByIdAndTenantId(platformId, c.getTenantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform");
        }
        c.apply(req.name().trim(), Search.blankToNull(req.phone()), Search.blankToNull(req.address()),
                platformId, Search.blankToNull(req.notes()));
        return CustomerResponse.of(customers.save(c));
    }
}
