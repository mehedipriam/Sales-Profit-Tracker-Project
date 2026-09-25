package com.salestracker.auth;

import com.salestracker.user.Role;

/** Authenticated principal extracted from the JWT; tenantId comes from the token, never from request input. */
public record AuthUser(Long userId, Long tenantId, String email, Role role, boolean remembered) {}
