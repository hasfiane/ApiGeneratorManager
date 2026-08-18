package com.api.generator.account.service;

import com.api.generator.account.AppUser;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.config.AccountProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    @Test
    void rotateLocalPasswordHashesNewSecretAndClearsResetTokens() {
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AccountService service = new AccountService(users, encoder, new AccountProperties());
        AppUser admin = new AppUser();
        admin.setEmail("admin@example.com");
        admin.setProvider(AppUser.Provider.LOCAL);
        admin.setPasswordHash("old-hash");
        admin.setPasswordResetTokenHash("reset-token-hash");
        admin.setPasswordResetExpiresAt(Instant.now().plusSeconds(600));
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(encoder.encode("new-admin-password-32-chars-minimum")).thenReturn("new-hash");
        when(users.save(admin)).thenReturn(admin);

        service.rotateLocalPassword("ADMIN@example.com ", "new-admin-password-32-chars-minimum");

        assertEquals("new-hash", admin.getPasswordHash());
        assertNull(admin.getPasswordResetTokenHash());
        assertNull(admin.getPasswordResetExpiresAt());
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertEquals(admin, saved.getValue());
    }

    @Test
    void rotateLocalPasswordRejectsFederatedAccounts() {
        AppUserRepository users = mock(AppUserRepository.class);
        AccountService service = new AccountService(users, mock(PasswordEncoder.class), new AccountProperties());
        AppUser admin = new AppUser();
        admin.setEmail("admin@example.com");
        admin.setProvider(AppUser.Provider.GOOGLE);
        admin.setPasswordHash("old-hash");
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(IllegalStateException.class, () -> service.rotateLocalPassword(
                "admin@example.com",
                "new-admin-password-32-chars-minimum"
        ));
    }
}
