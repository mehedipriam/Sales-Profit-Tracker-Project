package com.salestracker.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDtos {
    private AccountDtos() {}

    /** currentPassword is only required when the email (the login) changes. */
    public record ProfileRequest(@NotBlank @Size(max = 150) String fullName,
                                 @NotBlank @Email @Size(max = 190) String email,
                                 String currentPassword) {}

    public record PasswordRequest(@NotBlank String currentPassword,
                                  @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    /** currency is an ISO 4217 code such as BDT, USD or EUR. */
    public record BusinessRequest(@NotBlank @Size(max = 150) String name,
                                  @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {}
}
