package com.api.generator.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_app_user_email", columnList = "email", unique = true)
})
public class AppUser {

    public enum Provider { LOCAL, GOOGLE, GITHUB }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Provider provider = Provider.LOCAL;

    @Column(name = "google_sub", length = 128)
    private String googleSub;

    @Column(name = "provider_user_id", length = 160)
    private String providerUserId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean emailVerified = true;

    @Column(name = "email_verification_token_hash", length = 64)
    private String emailVerificationTokenHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Column(name = "roles", nullable = false, length = 255)
    private String roles = "ROLE_USER";

    @Column(name = "plan", nullable = false, length = 32)
    private String plan = "FREE";

    @Column(name = "plan_expires_at")
    private Instant planExpiresAt;

    @Column(name = "monthly_generation_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int monthlyGenerationCount;

    @Column(name = "monthly_docker_deployment_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int monthlyDockerDeploymentCount;

    @Column(name = "monthly_zip_download_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer monthlyZipDownloadCount = 0;

    @Column(name = "monthly_generation_period", length = 7)
    private String monthlyGenerationPeriod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public UUID getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }

    public String getProviderUserId() { return providerUserId; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getEmailVerificationTokenHash() { return emailVerificationTokenHash; }
    public void setEmailVerificationTokenHash(String emailVerificationTokenHash) { this.emailVerificationTokenHash = emailVerificationTokenHash; }

    public Instant getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public void setEmailVerificationExpiresAt(Instant emailVerificationExpiresAt) { this.emailVerificationExpiresAt = emailVerificationExpiresAt; }

    public String getPasswordResetTokenHash() { return passwordResetTokenHash; }
    public void setPasswordResetTokenHash(String passwordResetTokenHash) { this.passwordResetTokenHash = passwordResetTokenHash; }

    public Instant getPasswordResetExpiresAt() { return passwordResetExpiresAt; }
    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) { this.passwordResetExpiresAt = passwordResetExpiresAt; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public Instant getPlanExpiresAt() { return planExpiresAt; }
    public void setPlanExpiresAt(Instant planExpiresAt) { this.planExpiresAt = planExpiresAt; }

    public int getMonthlyGenerationCount() { return monthlyGenerationCount; }
    public void setMonthlyGenerationCount(int monthlyGenerationCount) { this.monthlyGenerationCount = monthlyGenerationCount; }

    public int getMonthlyDockerDeploymentCount() { return monthlyDockerDeploymentCount; }
    public void setMonthlyDockerDeploymentCount(int monthlyDockerDeploymentCount) { this.monthlyDockerDeploymentCount = monthlyDockerDeploymentCount; }

    public int getMonthlyZipDownloadCount() { return monthlyZipDownloadCount == null ? 0 : monthlyZipDownloadCount; }
    public void setMonthlyZipDownloadCount(int monthlyZipDownloadCount) { this.monthlyZipDownloadCount = monthlyZipDownloadCount; }

    public String getMonthlyGenerationPeriod() { return monthlyGenerationPeriod; }
    public void setMonthlyGenerationPeriod(String monthlyGenerationPeriod) { this.monthlyGenerationPeriod = monthlyGenerationPeriod; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
