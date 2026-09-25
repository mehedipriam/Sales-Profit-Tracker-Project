package com.salestracker.auth;

import com.salestracker.auth.AuthDtos.*;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.tenant.Tenant;
import com.salestracker.tenant.TenantRepository;
import com.salestracker.user.Role;
import com.salestracker.user.User;
import com.salestracker.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PlatformRepository platforms;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(TenantRepository tenants, UserRepository users, PlatformRepository platforms,
                       PasswordEncoder encoder, JwtService jwt) {
        this.tenants = tenants;
        this.users = users;
        this.platforms = platforms;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        Tenant tenant = tenants.save(new Tenant(req.businessName().trim()));
        platforms.saveAll(List.of(new Platform(tenant.getId(), "Facebook Page"),
                new Platform(tenant.getId(), "Daraz")));
        User user = users.save(new User(tenant.getId(), email, encoder.encode(req.password()),
                req.fullName().trim(), Role.OWNER));
        return toResponse(user, false);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        // Same message either way - a deactivated account shouldn't be distinguishable from a wrong password.
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(user, req.rememberMe());
    }

    @Transactional(readOnly = true)
    public UserInfo me(AuthUser principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        return toInfo(user);
    }

    /** A fresh token plus the user's details - after login, or after a change the token must reflect. */
    public AuthResponse toResponse(User user, boolean remembered) {
        return new AuthResponse(jwt.generate(user, remembered), toInfo(user));
    }

    public UserInfo toInfo(User u) {
        Tenant tenant = tenants.findById(u.getTenantId()).orElseThrow();
        return new UserInfo(u.getId(), u.getTenantId(), tenant.getName(), tenant.getCurrency(), u.getEmail(),
                u.getFullName(), u.getRole());
    }
}
