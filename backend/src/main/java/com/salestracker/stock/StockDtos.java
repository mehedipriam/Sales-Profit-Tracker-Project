package com.salestracker.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class StockDtos {
    private StockDtos() {}

    /** reason is RESTOCK (change above 0), DAMAGE (change below 0) or CORRECTION (either sign). */
    public record AdjustRequest(@NotNull Long productId, @NotNull Integer change, @NotNull StockReason reason,
                                @Size(max = 255) String note) {}

    public record AdjustmentResponse(Long id, LocalDateTime createdAt, Long productId, String productName,
                                     StockReason reason, int change, int stockAfter, String note, Long orderId) {}
}
