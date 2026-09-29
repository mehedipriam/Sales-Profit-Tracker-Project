package com.salestracker.activity;

import com.salestracker.activity.OrderActivityService.ActivityRow;
import com.salestracker.auth.AuthUser;
import com.salestracker.common.DateRange;
import com.salestracker.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** The activity log is for holding the team to account, so only Owner/Admin read it; everyone's actions are written. */
@RestController
@RequestMapping("/api/activity")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class OrderActivityController {
    private final OrderActivityService service;

    public OrderActivityController(OrderActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ActivityRow> list(@AuthenticationPrincipal AuthUser user,
                                          @RequestParam(required = false) Long orderId,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) OrderAction action,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "30") int size) {
        return service.list(user.tenantId(), orderId, userId, action, DateRange.of(from, to), page, size);
    }
}
