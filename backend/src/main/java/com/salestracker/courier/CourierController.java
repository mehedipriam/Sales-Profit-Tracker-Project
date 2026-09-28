package com.salestracker.courier;

import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.common.Search;
import com.salestracker.courier.CourierPayoutService.PayoutsResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The business's own courier list (everyone manages it, like platforms) and the courier payouts (Owner/Admin: it is
 * money the business is owed).
 */
@RestController
@RequestMapping("/api/couriers")
@Transactional
public class CourierController {
    /** trackingUrl must be a web link, never javascript: or the like, since the app renders it as a link. */
    public record CourierRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) @Pattern(regexp = "(?i)^\\s*(https?://\\S+)?\\s*$",
                    message = "must be a web link starting with http:// or https://") String trackingUrl) {}

    public record CourierResponse(Long id, String name, String trackingUrl) {
        static CourierResponse of(Courier c) {
            return new CourierResponse(c.getId(), c.getName(), c.getTrackingUrl());
        }
    }

    /** paidOn defaults to today. */
    public record PayoutRequest(@NotEmpty @Size(max = 500) List<Long> orderIds, LocalDate paidOn) {}

    public record UndoRequest(@NotEmpty @Size(max = 500) List<Long> orderIds) {}

    private final CourierRepository couriers;
    private final CourierPayoutService payouts;

    public CourierController(CourierRepository couriers, CourierPayoutService payouts) {
        this.couriers = couriers;
        this.payouts = payouts;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CourierResponse> list(@AuthenticationPrincipal AuthUser user) {
        return couriers.findByTenantIdAndActiveTrueOrderByName(user.tenantId()).stream()
                .map(CourierResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CourierRequest req) {
        return save(new Courier(user.tenantId(), req.name().trim()), req);
    }

    @PutMapping("/{id}")
    public CourierResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody CourierRequest req) {
        return save(find(user, id), req);
    }

    /** Deactivate rather than delete: existing orders keep their courier and any cash it still owes. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        find(user, id).deactivate();
    }

    @GetMapping("/payouts")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PayoutsResponse payouts(@AuthenticationPrincipal AuthUser user,
                                   @RequestParam(required = false) Long courierId) {
        return payouts.payouts(user.tenantId(), courierId);
    }

    @PostMapping("/payouts")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPaid(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PayoutRequest req) {
        payouts.markPaid(user.tenantId(), req.orderIds(), req.paidOn() != null ? req.paidOn() : LocalDate.now());
    }

    @PostMapping("/payouts/undo")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markUnpaid(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UndoRequest req) {
        payouts.markUnpaid(user.tenantId(), req.orderIds());
    }

    private Courier find(AuthUser user, Long id) {
        return couriers.findByIdAndTenantId(id, user.tenantId())
                .filter(Courier::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Courier not found"));
    }

    private CourierResponse save(Courier c, CourierRequest req) {
        String name = req.name().trim();
        Courier target = c;
        Optional<Courier> sameName = couriers.findByTenantIdAndName(c.getTenantId(), name)
                .filter(other -> !other.getId().equals(c.getId()));
        if (sameName.isPresent()) {
            // Adding back a courier that was removed earlier revives it rather than blocking its name for good.
            if (sameName.get().isActive() || c.getId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "Courier name already in use");
            }
            target = sameName.get();
            target.reactivate();
        }
        target.apply(name, Search.blankToNull(req.trackingUrl()));
        return CourierResponse.of(couriers.save(target));
    }
}
