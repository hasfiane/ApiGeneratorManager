package com.api.generator.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCookieServiceTest {

    @Test
    void setAuthCookieUsesConfiguredSecurityAttributes() {
        JwtCookieService service = new JwtCookieService(
                new JwtProperties("a-secure-secret-value-with-more-than-32-chars", "issuer", 3600),
                new JwtCookieProperties("APIGEN_AUTH", true, true, "Lax", "/", "example.com")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setAuthCookie(response, "jwt-token");

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("APIGEN_AUTH=jwt-token"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Domain=example.com"));
        assertTrue(cookie.contains("Max-Age=3600"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void clearAuthCookieExpiresConfiguredCookie() {
        JwtCookieService service = new JwtCookieService(
                new JwtProperties("a-secure-secret-value-with-more-than-32-chars", "issuer", 3600),
                new JwtCookieProperties("APIGEN_AUTH", true, true, "Lax", "/", "")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearAuthCookie(response);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("APIGEN_AUTH="));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
