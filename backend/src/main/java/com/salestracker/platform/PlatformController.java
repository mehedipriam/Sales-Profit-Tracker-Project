package com.salestracker.platform;

import com.salestracker.auth.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {
    public record PlatformResponse(Long id, String name, BigDecimal commissionPct) {}

    private final PlatformRepository platforms;

    public PlatformController(PlatformRepository platforms) {
        this.platforms = platforms;
    }

    @GetMapping
    public List<PlatformResponse> list(@AuthenticationPrincipal AuthUser user) {
        return platforms.findByTenantIdAndActiveTrueOrderByName(user.tenantId()).stream()
                .map(p -> new PlatformResponse(p.getId(), p.getName(), p.getCommissionPct()))
                .toList();
    }
}
