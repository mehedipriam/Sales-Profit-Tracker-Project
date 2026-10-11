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

    /** Unlocking cost prices and margins on screen: either the login password or the PIN, not both. */
    public record VerifyPasswordRequest(String password, String pin) {}

    /** Setting or changing the PIN (4-6 digits) needs the login password; a null pin removes it. */
    public record PinRequest(@NotBlank String currentPassword,
                             @Pattern(regexp = "\\d{4,6}", message = "must be 4 to 6 digits") String pin) {}

    public record PinStatus(boolean set, boolean locked) {}

    /** currency is an ISO 4217 code such as BDT, USD or EUR; phone, address and invoiceNote are optional. */
    public record BusinessRequest(@NotBlank @Size(max = 150) String name,
                                  @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
                                  @Size(max = 32) String phone,
                                  @Size(max = 500) String address,
                                  @Size(max = 500) String invoiceNote) {}
}
