package com.api.generator.runtime.web;

import com.api.generator.runtime.config.RuntimeSecurityProperties;
import com.api.generator.runtime.security.JwtTokenProvider;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(prefix = "security", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "authController")
public class AuthController {

    private final JwtTokenProvider tokens;
    private final PasswordEncoder encoder;
    private final RuntimeSecurityProperties securityProperties;

    public AuthController(
            JwtTokenProvider tokens,
            PasswordEncoder encoder,
            RuntimeSecurityProperties securityProperties
    ) {
        this.tokens = tokens;
        this.encoder = encoder;
        this.securityProperties = securityProperties;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token) {
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        String bootstrapUsername = securityProperties.getBootstrap().getUsername();
        String bootstrapPassword = securityProperties.getBootstrap().getPassword();

        if (bootstrapPassword == null || !bootstrapPassword.startsWith("$2")) {
            return ResponseEntity.status(503).build();
        }

        if (!bootstrapUsername.equals(req.username())) {
            return ResponseEntity.status(401).build();
        }

        boolean ok = encoder.matches(req.password(), bootstrapPassword);

        if (!ok) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new LoginResponse(tokens.generate(req.username())));
    }
}
