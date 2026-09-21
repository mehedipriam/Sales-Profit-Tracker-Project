package com.salestracker.auth;

import com.salestracker.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String businessName,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserInfo(Long id, Long tenantId, String businessName, String email, String fullName, Role role) {}

    public record AuthResponse(String token, UserInfo user) {}
}
