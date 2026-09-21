package com.salestracker.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank @Size(max = 190) String name,
            @Size(max = 64) String sku,
            @Size(max = 100) String category,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
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
    }
}
