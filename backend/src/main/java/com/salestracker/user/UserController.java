package com.salestracker.user;

import com.salestracker.auth.AuthUser;
import com.salestracker.user.UserDtos.StaffRequest;
import com.salestracker.user.UserDtos.StaffResponse;
import com.salestracker.user.UserDtos.StaffUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Team management on the caller's own tenant: an Owner manages everyone, an Admin manages Staff (see UserService). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffResponse> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody StaffRequest req) {
        return service.create(user, req);
    }

    @PutMapping("/{id}")
    public StaffResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                @Valid @RequestBody StaffUpdateRequest req) {
        return service.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.deactivate(user, id);
    }
}
