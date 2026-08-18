package com.api.generator.admin;

import com.api.generator.account.AppUser;
import com.api.generator.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.CacheControl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSecretRotationControllerTest {

    @Test
    void rotatesAuthenticatedAdminPasswordAndReturnsSecretOnceWithNoStore() {
        AccountService accountService = mock(AccountService.class);
        AppUser rotated = new AppUser();
        rotated.setEmail("admin@example.com");
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        when(accountService.rotateLocalPassword(eq("admin@example.com"), anyString())).thenReturn(rotated);
        AdminSecretRotationController controller = new AdminSecretRotationController(accountService, new SecureRandom());

        var response = controller.rotateAdminPassword(
                new UsernamePasswordAuthenticationToken("admin@example.com", "ignored")
        );

        assertEquals(CacheControl.noStore().getHeaderValue(), response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
        assertEquals("admin@example.com", response.getBody().email());
        assertTrue(response.getBody().temporaryPassword().length() >= 40);
        assertNotNull(response.getBody().rotatedAt());
        verify(accountService).rotateLocalPassword(eq("admin@example.com"), passwordCaptor.capture());
        assertEquals(passwordCaptor.getValue(), response.getBody().temporaryPassword());
    }

    @Test
    void rejectsMissingAuthentication() {
        AdminSecretRotationController controller = new AdminSecretRotationController(mock(AccountService.class), new SecureRandom());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.rotateAdminPassword(null));

        assertEquals(401, error.getStatusCode().value());
    }
}
