package com.salestracker.account;

import com.salestracker.account.AccountDtos.BusinessRequest;
import com.salestracker.account.AccountDtos.PasswordRequest;
import com.salestracker.account.AccountDtos.PinRequest;
import com.salestracker.account.AccountDtos.PinStatus;
import com.salestracker.account.AccountDtos.ProfileRequest;
import com.salestracker.account.AccountDtos.VerifyPasswordRequest;
import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthDtos.AuthResponse;
import com.salestracker.auth.AuthDtos.UserInfo;
import com.salestracker.auth.AuthService;
import com.salestracker.auth.AuthUser;
import com.salestracker.common.Search;
import com.salestracker.tenant.Tenant;
import com.salestracker.tenant.TenantRepository;
import com.salestracker.user.User;
import com.salestracker.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

/**
 * The signed-in user's own account: their name, login email and password, and (Owner only) the business name and the
 * currency its amounts are shown in.
 */
@Service
@Transactional
public class AccountService {
    /** Wrong PINs in a row before the PIN stops working until the login password is entered. */
    static final int PIN_TRIES = 5;

    private final UserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder encoder;
    private final AuthService auth;

    public AccountService(UserRepository users, TenantRepository tenants, PasswordEncoder encoder, AuthService auth) {
        this.users = users;
        this.tenants = tenants;
        this.encoder = encoder;
        this.auth = auth;
    }

    /** Returns a fresh token, since the token carries the email. */
    public AuthResponse updateProfile(AuthUser principal, ProfileRequest req) {
        User user = current(principal);
        String email = req.email().trim().toLowerCase();
        if (!email.equals(user.getEmail())) {
            // The email is the login, so changing it is guarded like a password change.
            checkPassword(user, req.currentPassword());
            if (users.existsByEmail(email)) {
                throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
            }
            user.changeEmail(email);
        }
        user.rename(req.fullName().trim());
        return auth.toResponse(user, principal.remembered());
    }

    public void changePassword(AuthUser principal, PasswordRequest req) {
        User user = current(principal);
        checkPassword(user, req.currentPassword());
        user.changePassword(encoder.encode(req.newPassword()));
    }

    /**
     * Checks the login password or the PIN. A wrong PIN is counted (so the transaction must not roll back on the
     * error); after {@link #PIN_TRIES} in a row only the password works, and a correct password lifts that lock.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyPassword(AuthUser principal, VerifyPasswordRequest req) {
        User user = current(principal);
        boolean byPassword = req.password() != null && !req.password().isEmpty();
        boolean byPin = req.pin() != null && !req.pin().isEmpty();
        if (byPassword == byPin) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your password or your PIN");
        }
        if (byPassword) {
            if (!encoder.matches(req.password(), user.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect password");
            }
            user.pinSucceeded();
            return;
        }
        if (user.getPinHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You have not set a PIN - set one in Settings");
        }
        if (user.getPinFailures() >= PIN_TRIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many wrong PINs - enter your password instead");
        }
        if (!encoder.matches(req.pin(), user.getPinHash())) {
            user.pinFailed();
            int left = PIN_TRIES - user.getPinFailures();
            throw new ApiException(HttpStatus.BAD_REQUEST, left > 0
                    ? "Incorrect PIN (%d %s left)".formatted(left, left == 1 ? "try" : "tries")
                    : "Too many wrong PINs - enter your password instead");
        }
        user.pinSucceeded();
    }

    @Transactional(readOnly = true)
    public PinStatus pinStatus(AuthUser principal) {
        User user = current(principal);
        return new PinStatus(user.getPinHash() != null, user.getPinFailures() >= PIN_TRIES);
    }

    public PinStatus changePin(AuthUser principal, PinRequest req) {
        User user = current(principal);
        checkPassword(user, req.currentPassword());
        user.changePin(req.pin() == null ? null : encoder.encode(req.pin()));
        return new PinStatus(req.pin() != null, false);
    }

    public UserInfo renameBusiness(AuthUser principal, BusinessRequest req) {
        Tenant tenant = tenants.findById(principal.tenantId()).orElseThrow();
        String currency = req.currency().toUpperCase();
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown currency: " + currency);
        }
        tenant.rename(req.name().trim());
        tenant.changeCurrency(currency);
        tenant.changeContact(Search.blankToNull(req.phone()), Search.blankToNull(req.address()),
                Search.blankToNull(req.invoiceNote()));
        return auth.toInfo(current(principal));
    }

    private User current(AuthUser principal) {
        return users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private void checkPassword(User user, String password) {
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
    }
}
