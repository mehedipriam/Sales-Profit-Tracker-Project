package com.salestracker.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {}

    /** role is optional - omitted creates a Staff account. */
    public record StaffRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            Role role) {}

    /** password and role are optional - blank/omitted leaves them unchanged. */
    public record StaffUpdateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 72) String password,
            Role role) {}

    public record StaffResponse(Long id, String fullName, String email, Role role, boolean active) {
        static StaffResponse of(User u) {
            return new StaffResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive());
        }
    }
}
