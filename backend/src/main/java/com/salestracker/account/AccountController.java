package com.salestracker.account;

import com.salestracker.account.AccountDtos.BusinessRequest;
import com.salestracker.account.AccountDtos.PasswordRequest;
import com.salestracker.account.AccountDtos.PinRequest;
import com.salestracker.account.AccountDtos.PinStatus;
import com.salestracker.account.AccountDtos.ProfileRequest;
import com.salestracker.account.AccountDtos.VerifyPasswordRequest;
import com.salestracker.auth.AuthDtos.AuthResponse;
import com.salestracker.auth.AuthDtos.UserInfo;
import com.salestracker.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Settings for the signed-in user; Owner and Staff both manage their own profile and password. */
@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PutMapping("/profile")
    public AuthResponse updateProfile(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ProfileRequest req) {
        return service.updateProfile(user, req);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PasswordRequest req) {
        service.changePassword(user, req);
    }

    /**
     * Takes the login password or the PIN: 204 when it is the signed-in user's own; 400 otherwise (not 401, which
     * would sign them out).
     */
    @PostMapping("/verify-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyPassword(@AuthenticationPrincipal AuthUser user, @RequestBody VerifyPasswordRequest req) {
        service.verifyPassword(user, req);
    }

    /** Whether the user has a PIN (never the PIN itself, which is stored hashed) and whether wrong guesses locked it. */
    @GetMapping("/pin")
    public PinStatus pinStatus(@AuthenticationPrincipal AuthUser user) {
        return service.pinStatus(user);
    }

    /** Sets, changes or (with a null pin) removes the PIN; the login password is required either way. */
    @PutMapping("/pin")
    public PinStatus changePin(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PinRequest req) {
        return service.changePin(user, req);
    }

    @PutMapping("/business")
    @PreAuthorize("hasRole('OWNER')")
    public UserInfo renameBusiness(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BusinessRequest req) {
        return service.renameBusiness(user, req);
    }
}
