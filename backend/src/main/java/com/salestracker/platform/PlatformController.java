package com.salestracker.platform;

import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/platforms")
@Transactional
public class PlatformController {
    public record PlatformRequest(
            @NotBlank @Size(max = 100) String name,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct) {}

    public record PlatformResponse(Long id, String name, BigDecimal commissionPct) {
        static PlatformResponse of(Platform p) {
            return new PlatformResponse(p.getId(), p.getName(), p.getCommissionPct());
        }
    }

    private final PlatformRepository platforms;

    public PlatformController(PlatformRepository platforms) {
        this.platforms = platforms;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PlatformResponse> list(@AuthenticationPrincipal AuthUser user) {
        return platforms.findByTenantIdAndActiveTrueOrderByName(user.tenantId()).stream()
                .map(PlatformResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PlatformRequest req) {
        return save(user, new Platform(user.tenantId(), req.name().trim()), req);
    }

    @PutMapping("/{id}")
    public PlatformResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @Valid @RequestBody PlatformRequest req) {
        return save(user, find(user, id), req);
    }

    /** Deactivate rather than delete: existing orders keep pointing at the platform. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        find(user, id).deactivate();
    }

    private Platform find(AuthUser user, Long id) {
        return platforms.findByIdAndTenantId(id, user.tenantId())
                .filter(Platform::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Platform not found"));
    }

    /** Staff (Phase 7b) get full platform management except the commission rate, which stays Owner-only. */
    private PlatformResponse save(AuthUser user, Platform p, PlatformRequest req) {
        String name = req.name().trim();
        platforms.findByTenantIdAndName(p.getTenantId(), name)
                .filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "Platform name already in use"); });

        BigDecimal requested = req.commissionPct() == null ? BigDecimal.ZERO : req.commissionPct();
        BigDecimal commissionPct;
        if (user.role().seesFinancials()) {
            commissionPct = requested;
        } else if (p.getId() == null) {
            commissionPct = BigDecimal.ZERO; // a Staff-created platform starts at 0%; an Owner or Admin sets the real rate later
        } else if (requested.compareTo(p.getCommissionPct()) != 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an owner or admin can change the commission rate");
        } else {
            commissionPct = p.getCommissionPct();
        }
        p.apply(name, commissionPct);
        return PlatformResponse.of(platforms.save(p));
    }
}
