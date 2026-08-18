package com.api.generator.admin;

import com.api.generator.account.AppUser;
import com.api.generator.account.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping(path = "/api/admin/secrets", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminSecretRotationController {

    private static final int PASSWORD_RANDOM_BYTES = 32;

    private final AccountService accountService;
    private final SecureRandom secureRandom;

    @Autowired
    public AdminSecretRotationController(AccountService accountService) {
        this(accountService, new SecureRandom());
    }

    AdminSecretRotationController(AccountService accountService, SecureRandom secureRandom) {
        this.accountService = accountService;
        this.secureRandom = secureRandom;
    }

    @PostMapping("/admin-password/rotate")
    public ResponseEntity<AdminSecretRotationView> rotateAdminPassword(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }

        String temporaryPassword = generateTemporaryPassword();
        try {
            AppUser rotated = accountService.rotateLocalPassword(authentication.getName(), temporaryPassword);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new AdminSecretRotationView(
                            rotated.getEmail(),
                            temporaryPassword,
                            Instant.now().toString(),
                            "Admin password rotated. Store the new password now; it will not be shown again."
                    ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }
    }

    private String generateTemporaryPassword() {
        byte[] bytes = new byte[PASSWORD_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AdminSecretRotationView(
            String email,
            String temporaryPassword,
            String rotatedAt,
            String message
    ) {
    }
}
