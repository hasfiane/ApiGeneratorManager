package com.api.generator.account.repo;

import com.api.generator.account.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByEmailVerificationTokenHash(String emailVerificationTokenHash);
    Optional<AppUser> findByPasswordResetTokenHash(String passwordResetTokenHash);
    Optional<AppUser> findByProviderAndProviderUserId(AppUser.Provider provider, String providerUserId);
}
