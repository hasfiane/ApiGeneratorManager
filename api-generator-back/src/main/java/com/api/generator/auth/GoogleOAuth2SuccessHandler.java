package com.api.generator.auth;

import com.api.generator.account.service.AccountService;
import com.api.generator.config.OAuth2Properties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accounts;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final OAuth2Properties oauth2Properties;

    public GoogleOAuth2SuccessHandler(AccountService accounts,
                                      JwtService jwtService,
                                      JwtCookieService jwtCookieService,
                                      OAuth2Properties oauth2Properties) {
        this.accounts = accounts;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        org.springframework.security.core.Authentication authentication)
            throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            response.sendError(500, "Unexpected authentication type");
            return;
        }

        Map<String, Object> attrs = oauth.getPrincipal().getAttributes();
        String email = String.valueOf(attrs.getOrDefault("email", ""));
        String name = String.valueOf(attrs.getOrDefault("name", ""));
        String sub = String.valueOf(attrs.getOrDefault("sub", ""));
        boolean emailVerified = Boolean.TRUE.equals(attrs.get("email_verified"))
                || "true".equalsIgnoreCase(String.valueOf(attrs.getOrDefault("email_verified", "false")));

        if (email == null || email.isBlank()) {
            response.sendError(400, "Google account has no email");
            return;
        }
        if (!emailVerified) {
            response.sendError(403, "Google account email is not verified");
            return;
        }

        com.api.generator.account.AppUser user;
        try {
            user = accounts.onGoogleLogin(email, name, sub);
        } catch (IllegalStateException ex) {
            response.sendError(403, ex.getMessage());
            return;
        }

        // Build a UserDetails for JWT generation
        List<SimpleGrantedAuthority> authorities = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SimpleGrantedAuthority::new)
                .toList();
        var ud = User.withUsername(user.getEmail())
                .password("{noop}oauth2")
                .authorities(authorities)
                .build();

        String token = jwtService.generate(ud);
        jwtCookieService.setAuthCookie(response, token);
        response.sendRedirect(oauth2Properties.getSuccessRedirect());
    }
}
