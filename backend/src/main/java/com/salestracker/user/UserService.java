package com.salestracker.user;

import com.salestracker.auth.ApiException;
import com.salestracker.user.UserDtos.StaffRequest;
import com.salestracker.user.UserDtos.StaffResponse;
import com.salestracker.user.UserDtos.StaffUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Staff accounts within a tenant. Owner accounts are never created here - only by registering a business. */
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

    public StaffResponse create(Long tenantId, StaffRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User u = new User(tenantId, email, encoder.encode(req.password()), req.fullName().trim(), Role.STAFF);
        return StaffResponse.of(users.save(u));
    }

    public StaffResponse update(Long tenantId, Long id, StaffUpdateRequest req) {
        User u = find(tenantId, id);
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
    public void deactivate(Long tenantId, Long id) {
        User u = find(tenantId, id);
        if (u.getRole() == Role.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "The owner account cannot be deactivated");
        }
        u.deactivate();
    }

    private User find(Long tenantId, Long id) {
        return users.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
