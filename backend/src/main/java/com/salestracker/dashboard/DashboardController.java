package com.salestracker.dashboard;

import com.salestracker.auth.AuthUser;
import com.salestracker.dashboard.DashboardService.DashboardResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse get(@AuthenticationPrincipal AuthUser user) {
        return service.get(user.tenantId());
    }
}
