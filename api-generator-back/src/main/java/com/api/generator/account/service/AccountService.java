package com.api.generator.account.service;

import com.api.generator.account.AppUser;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.config.AccountProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AccountProperties accountProperties;

    public AccountService(AppUserRepository users, PasswordEncoder encoder, AccountProperties accountProperties) {
        this.users = users;
        this.encoder = encoder;
        this.accountProperties = accountProperties;
    }

    public RegistrationResult registerLocal(String email, String rawPassword, boolean requireEmailVerification, long verificationExpirationMinutes) {
        String normalized = normalizeEmail(email);
        users.findByEmailIgnoreCase(normalized).ifPresent(u -> {
            throw new IllegalArgumentException("Email already exists");
        });

        String verificationToken = requireEmailVerification ? UUID.randomUUID().toString() + UUID.randomUUID() : null;
        AppUser u = new AppUser();
        u.setEmail(normalized);
        u.setProvider(AppUser.Provider.LOCAL);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setEmailVerified(!requireEmailVerification);
        if (requireEmailVerification) {
            u.setEmailVerificationTokenHash(hashToken(verificationToken));
            u.setEmailVerificationExpiresAt(Instant.now().plusSeconds(verificationExpirationMinutes * 60));
        }
        u.setPlan(accountProperties.getDefaultPlan());
        if (u.getRoles() == null) u.setRoles(accountProperties.getDefaultRoles());
        return new RegistrationResult(users.save(u), verificationToken);
    }

    public AppUser onLocalLogin(String email) {
        String normalized = normalizeEmail(email);
        AppUser u = users.findByEmailIgnoreCase(normalized).orElseGet(() -> {
            // If you still have legacy in-memory users, we mirror them into DB on first successful login.
            AppUser nu = new AppUser();
            nu.setEmail(normalized);
            nu.setProvider(AppUser.Provider.LOCAL);
            nu.setPlan(accountProperties.getLegacyPlan());
            nu.setRoles(accountProperties.getDefaultRoles());
            return nu;
        });
        if (!u.isEnabled()) {
            throw new IllegalStateException("User disabled");
        }
        if (!u.isEmailVerified()) {
            throw new IllegalStateException("Email not verified");
        }
        u.setLastLoginAt(Instant.now());
        if (u.getPlan() == null || u.getPlan().isBlank()) u.setPlan(accountProperties.getDefaultPlan());
        if (u.getRoles() == null || u.getRoles().isBlank()) u.setRoles(accountProperties.getDefaultRoles());
        return users.save(u);
    }

    public AppUser onGoogleLogin(String email, String displayName, String googleSub) {
        String normalized = normalizeEmail(email);
        AppUser u = users.findByProviderAndProviderUserId(AppUser.Provider.GOOGLE, googleSub)
                .or(() -> users.findByEmailIgnoreCase(normalized))
                .orElseGet(AppUser::new);
        if (!u.isEnabled()) {
            throw new IllegalStateException("User disabled");
        }
        if (u.getId() != null && u.getProvider() == AppUser.Provider.LOCAL && u.getPasswordHash() != null && u.getProviderUserId() == null) {
            throw new IllegalStateException("Email already registered with local authentication");
        }
        u.setEmail(normalized);
        u.setProvider(AppUser.Provider.GOOGLE);
        u.setProviderUserId(googleSub);
        u.setEmailVerified(true);
        u.setEmailVerificationTokenHash(null);
        u.setEmailVerificationExpiresAt(null);
        u.setDisplayName(displayName);
        u.setGoogleSub(googleSub);
        if (u.getPlan() == null || u.getPlan().isBlank()) u.setPlan(accountProperties.getDefaultPlan());
        if (u.getRoles() == null || u.getRoles().isBlank()) u.setRoles(accountProperties.getDefaultRoles());
        u.setLastLoginAt(Instant.now());
        return users.save(u);
    }

    @Transactional
    public void incrementMonthlyGeneration(AppUser user, PlanCapabilityService capabilities) {
        capabilities.markGenerationStarted(user);
        users.save(user);
    }

    @Transactional
    public void incrementMonthlyDockerDeployment(UUID userId, PlanCapabilityService capabilities) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        capabilities.markDockerDeploymentSucceeded(user);
        users.save(user);
    }

    @Transactional
    public void incrementMonthlyZipDownload(UUID userId, PlanCapabilityService capabilities) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        capabilities.ensureCanDownloadZip(user);
        capabilities.markZipDownloaded(user);
        users.save(user);
    }

    public Optional<AppUser> verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        return users.findByEmailVerificationTokenHash(hashToken(rawToken.trim()))
                .filter(user -> user.getEmailVerificationExpiresAt() != null)
                .filter(user -> user.getEmailVerificationExpiresAt().isAfter(Instant.now()))
                .map(user -> {
                    user.setEmailVerified(true);
                    user.setEmailVerificationTokenHash(null);
                    user.setEmailVerificationExpiresAt(null);
                    return users.save(user);
                });
    }

    public Optional<RegistrationResult> createEmailVerificationToken(String email, long expirationMinutes) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalizeEmail(email);
        return users.findByEmailIgnoreCase(normalized)
                .filter(AppUser::isEnabled)
                .filter(user -> user.getProvider() == AppUser.Provider.LOCAL)
                .filter(user -> !user.isEmailVerified())
                .map(user -> {
                    String token = UUID.randomUUID().toString() + UUID.randomUUID();
                    user.setEmailVerificationTokenHash(hashToken(token));
                    user.setEmailVerificationExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));
                    return new RegistrationResult(users.save(user), token);
                });
    }

    public Optional<String> createPasswordResetToken(String email, long expirationMinutes) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeEmail(email);
        return users.findByEmailIgnoreCase(normalized)
                .filter(AppUser::isEnabled)
                .filter(user -> user.getProvider() == AppUser.Provider.LOCAL)
                .filter(user -> user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .map(user -> {
                    String token = UUID.randomUUID().toString() + UUID.randomUUID();
                    user.setPasswordResetTokenHash(hashToken(token));
                    user.setPasswordResetExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));
                    users.save(user);
                    return token;
                });
    }

    public Optional<AppUser> resetPassword(String rawToken, String rawPassword) {
        if (rawToken == null || rawToken.isBlank() || rawPassword == null || rawPassword.length() < 8) {
            return Optional.empty();
        }
        return users.findByPasswordResetTokenHash(hashToken(rawToken.trim()))
                .filter(user -> user.getPasswordResetExpiresAt() != null)
                .filter(user -> user.getPasswordResetExpiresAt().isAfter(Instant.now()))
                .filter(user -> user.getProvider() == AppUser.Provider.LOCAL)
                .map(user -> {
                    user.setPasswordHash(encoder.encode(rawPassword));
                    user.setPasswordResetTokenHash(null);
                    user.setPasswordResetExpiresAt(null);
                    return users.save(user);
                });
    }

    @Transactional
    public AppUser rotateLocalPassword(String email, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 16 || rawPassword.length() > 128) {
            throw new IllegalArgumentException("Generated password does not meet rotation policy");
        }
        String normalized = normalizeEmail(email);
        AppUser user = users.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found"));
        if (!user.isEnabled()) {
            throw new IllegalStateException("Admin account disabled");
        }
        if (user.getProvider() != AppUser.Provider.LOCAL || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new IllegalStateException("Admin password can only be rotated for local accounts");
        }
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        return users.save(user);
    }

    public AppUser findByEmailOrNull(String email) {
        String normalized = normalizeEmail(email);
        return users.findByEmailIgnoreCase(normalized).orElse(null);
    }

    public String defaultPlan() {
        return accountProperties.getDefaultPlan();
    }

    public String defaultRoles() {
        return accountProperties.getDefaultRoles();
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("email is required");
        String e = email.trim().toLowerCase();
        if (e.isBlank()) throw new IllegalArgumentException("email is required");
        return e;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record RegistrationResult(AppUser user, String verificationToken) {}
}
