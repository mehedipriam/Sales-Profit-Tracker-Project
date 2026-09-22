package com.salestracker.product;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.PageResponse;
import com.salestracker.product.ProductDtos.ProductRequest;
import com.salestracker.product.ProductDtos.ProductResponse;
import com.salestracker.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.UnaryOperator;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(user.tenantId(), q, category, page, size).map(redaction(user));
    }

    @GetMapping("/categories")
    public List<String> categories(@AuthenticationPrincipal AuthUser user) {
        return service.categories(user.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ProductRequest req) {
        return redaction(user).apply(service.create(user.tenantId(), req));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody ProductRequest req) {
        return redaction(user).apply(service.update(user.tenantId(), id, req));
    }

    /** Phase 7b: cost price (and margin) is Owner-only; Staff still needs the rest to run sales day to day. */
    private static UnaryOperator<ProductResponse> redaction(AuthUser user) {
        return user.role() == Role.OWNER ? r -> r : ProductResponse::hideCost;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.tenantId(), id);
    }
}
