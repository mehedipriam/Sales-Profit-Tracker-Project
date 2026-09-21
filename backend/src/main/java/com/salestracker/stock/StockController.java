package com.salestracker.stock;

import com.salestracker.auth.AuthUser;
import com.salestracker.common.PageResponse;
import com.salestracker.stock.StockDtos.AdjustRequest;
import com.salestracker.stock.StockDtos.AdjustmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock/adjustments")
public class StockController {
    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    /** The stock log, newest first. */
    @GetMapping
    public PageResponse<AdjustmentResponse> list(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(required = false) Long productId,
                                                 @RequestParam(required = false) StockReason reason,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.list(user.tenantId(), productId, reason, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse adjust(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AdjustRequest req) {
        return service.adjust(user.tenantId(), req);
    }
}
