package com.salestracker.user;

import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.user.UserDtos.StaffRequest;
import com.salestracker.user.UserDtos.StaffResponse;
import com.salestracker.user.UserDtos.StaffUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Team accounts within a tenant. An Owner manages everyone (any role). An Admin manages Staff only: they can't
 * create, edit, promote or remove an Owner or Admin, so they can't take over an Owner's account or raise their own.
 */
@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list(Long tenantId) {
        return users.findByTenantIdOrderByFullName(tenantId).stream().map(StaffResponse::of).toList();
    }

    public StaffResponse create(AuthUser actor, StaffRequest req) {
        Role role = req.role() == null ? Role.STAFF : req.role();
        requireCanAssign(actor, role);
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User u = new User(actor.tenantId(), email, encoder.encode(req.password()), req.fullName().trim(), role);
        return StaffResponse.of(users.save(u));
    }

    public StaffResponse update(AuthUser actor, Long id, StaffUpdateRequest req) {
        User u = find(actor.tenantId(), id);
        boolean self = u.getId().equals(actor.userId());
        if (!self) requireCanManage(actor, u);
        if (req.role() != null && req.role() != u.getRole()) {
            // Blocking a change to your own role also guarantees the business always keeps at least one Owner.
            if (self) {
                throw new ApiException(HttpStatus.CONFLICT, "You can't change your own role");
            }
            requireCanAssign(actor, req.role());
            u.changeRole(req.role());
        }
        u.rename(req.fullName().trim());
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 8) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
            }
            u.changePassword(encoder.encode(req.password()));
        }
        return StaffResponse.of(u);
    }

    /** Revokes access; the account and its history (orders recorded, etc.) stay. */
    public void deactivate(AuthUser actor, Long id) {
        User u = find(actor.tenantId(), id);
        if (u.getId().equals(actor.userId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You can't remove your own access");
        }
        requireCanManage(actor, u);
        if (u.getRole() == Role.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "An Owner account cannot be deactivated - change their role to Staff first");
        }
        u.deactivate();
    }

    /** An Admin may only act on Staff accounts; an Owner may act on anyone. */
    private static void requireCanManage(AuthUser actor, User target) {
        if (actor.role() != Role.OWNER && target.getRole() != Role.STAFF) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an owner can manage owner and admin accounts");
        }
    }

    /** An Admin may only hand out the Staff role; an Owner may hand out any. */
    private static void requireCanAssign(AuthUser actor, Role role) {
        if (actor.role() != Role.OWNER && role != Role.STAFF) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an owner can give someone the owner or admin role");
        }
    }

    private User find(Long tenantId, Long id) {
        return users.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
