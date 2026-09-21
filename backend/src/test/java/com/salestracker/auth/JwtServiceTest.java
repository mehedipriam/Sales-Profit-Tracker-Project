package com.salestracker.auth;

import com.salestracker.user.Role;
import com.salestracker.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-that-is-long-enough-0123456789";

    @Test
    void roundTripKeepsTenantAndRole() {
        JwtService jwt = new JwtService(SECRET, 10);
        User user = new User(7L, "a@b.com", "hash", "A B", Role.STAFF);
        ReflectionTestUtils.setField(user, "id", 42L);
        String token = jwt.generate(user);

        AuthUser parsed = jwt.parse(token).orElseThrow();
        assertEquals(42L, parsed.userId());
        assertEquals(7L, parsed.tenantId());
        assertEquals(Role.STAFF, parsed.role());
        assertEquals("a@b.com", parsed.email());
    }

    @Test
    void tamperedOrForeignTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 10);
        JwtService other = new JwtService("another-secret-that-is-long-enough-9876543210", 10);
        User user = new User(1L, "x@y.com", "h", "X", Role.OWNER);
        ReflectionTestUtils.setField(user, "id", 1L);
        String token = other.generate(user);

        assertTrue(jwt.parse(token).isEmpty());
        assertTrue(jwt.parse("not-a-jwt").isEmpty());
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 10));
    }
}
