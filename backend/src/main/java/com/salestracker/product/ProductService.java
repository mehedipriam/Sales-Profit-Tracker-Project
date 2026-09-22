package com.salestracker.product;

import com.salestracker.auth.ApiException;
import com.salestracker.common.PageResponse;
import com.salestracker.common.Search;
import com.salestracker.product.ProductDtos.ProductRequest;
import com.salestracker.product.ProductDtos.ProductResponse;
import com.salestracker.stock.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final StockService stock;

    public ProductService(ProductRepository products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Long tenantId, String q, String category, int page, int size) {
        String cat = category == null ? "" : category.trim();
        return PageResponse.of(
                products.search(tenantId, Search.pattern(q), cat, Search.page(page, size, "name")),
                ProductResponse::of);
    }

    @Transactional(readOnly = true)
    public List<String> categories(Long tenantId) {
        return products.categories(tenantId);
    }

    public ProductResponse create(Long tenantId, ProductRequest req) {
        Product p = new Product(tenantId);
        return save(p, req);
    }

    public ProductResponse update(Long tenantId, Long id, ProductRequest req) {
        return save(find(tenantId, id), req);
    }

    /** Soft delete: past orders keep referencing the product. */
    public void delete(Long tenantId, Long id) {
        find(tenantId, id).deactivate();
    }

    private Product find(Long tenantId, Long id) {
        return products.findByIdAndTenantIdAndActiveTrue(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private ProductResponse save(Product p, ProductRequest req) {
        String sku = Search.blankToNull(req.sku());
        if (sku != null) {
            products.findByTenantIdAndSku(p.getTenantId(), sku)
                    .filter(other -> !other.getId().equals(p.getId()))
                    .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "SKU already in use"); });
        }
        boolean isNew = p.getId() == null;
        // A Staff request never carries a real cost price (they can't see it to resubmit it) - keep the
        // existing one on an edit, or 0 (pending the owner) on a brand new product.
        java.math.BigDecimal costPrice = req.costPrice() != null ? req.costPrice()
                : isNew ? java.math.BigDecimal.ZERO : p.getCostPrice();
        Integer stockBefore = p.getStockQty();
        p.apply(req.name().trim(), sku, Search.blankToNull(req.category()),
                costPrice, req.sellingPrice(), req.stockQty(), req.lowStockThreshold());
        Product saved = products.save(p);
        stock.recordProductEdit(saved, stockBefore);
        return ProductResponse.of(saved);
    }
}
