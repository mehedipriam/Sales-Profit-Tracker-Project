package com.salestracker.auth;

import com.salestracker.tenant.TenantContext;
import com.salestracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                jwtService.parse(header.substring(7))
                        // A JWT can outlive a deactivation (tokens last 8h - see app.jwt.expiration-minutes) -
                        // re-checking active status here, not just at login, makes revoking Staff access immediate
                        // rather than "eventually, once their token expires".
                        .filter(user -> users.existsByIdAndActiveTrue(user.userId()))
                        .ifPresent(user -> {
                            TenantContext.set(user.tenantId());
                            var auth = new UsernamePasswordAuthenticationToken(
                                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
            chain.doFilter(request, response);
        } finally {
            // This thread returns to the pool after the response is written; clearing here stops one
            // request's tenant leaking into the next request that happens to land on the same thread.
            TenantContext.clear();
        }
    }
}
