package com.salestracker.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountDtos {
    private AccountDtos() {}

    /** currentPassword is only required when the email (the login) changes. */
    public record ProfileRequest(@NotBlank @Size(max = 150) String fullName,
                                 @NotBlank @Email @Size(max = 190) String email,
                                 String currentPassword) {}

    public record PasswordRequest(@NotBlank String currentPassword,
                                  @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record BusinessRequest(@NotBlank @Size(max = 150) String name) {}
}
