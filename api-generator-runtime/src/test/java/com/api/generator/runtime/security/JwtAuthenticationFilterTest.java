package com.api.generator.runtime.security;

import com.api.generator.runtime.config.RuntimeSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresRolesFromValidGeneratedApiToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider());
        String token = provider().generate("operator", List.of("ADMIN", "ROLE_AUDITOR"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertTrue(continued.get());
        assertEquals("operator", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_AUDITOR")));
    }

    @Test
    void ignoresInvalidTokenAndContinuesUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertTrue(continued.get());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
    }

    private static JwtTokenProvider provider() {
        RuntimeSecurityProperties props = new RuntimeSecurityProperties();
        props.getJwt().setSecret("01234567890123456789012345678901");
        props.getJwt().setIssuer("generated-api");
        props.getJwt().setAudience("generated-api");
        return new JwtTokenProvider(props);
    }
}
