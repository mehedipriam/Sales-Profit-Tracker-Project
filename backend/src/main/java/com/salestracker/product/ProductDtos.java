package com.salestracker.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {}

    /**
     * costPrice is intentionally not @NotNull: Staff (Phase 7b) never sees a product's cost price, so they
     * can't be made to re-submit it on every edit just to rename a product. Omitting it means "leave the
     * cost price alone" on an update, or "0, an Owner will set the real one later" on a brand new product -
     * see ProductService.save.
     */
    public record ProductRequest(
            @NotBlank @Size(max = 190) String name,
            @Size(max = 64) String sku,
            @Size(max = 100) String category,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
            @Min(0) Integer stockQty,
            @Min(0) Integer lowStockThreshold) {}

    public record ProductResponse(Long id, String name, String sku, String category,
                                  BigDecimal costPrice, BigDecimal sellingPrice, Integer stockQty,
                                  Integer lowStockThreshold) {
        static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getCategory(),
                    p.getCostPrice(), p.getSellingPrice(), p.getStockQty(), p.getLowStockThreshold());
        }

        /** Phase 7b: Staff record and see sales, but cost price (and so margin) is Owner-only. */
        public ProductResponse hideCost() {
            return new ProductResponse(id, name, sku, category, null, sellingPrice, stockQty, lowStockThreshold);
        }
    }
}
