package com.api.generator.auth;

import com.api.generator.account.AppUser;
import com.api.generator.account.service.AccountService;
import com.api.generator.account.service.PlanCapabilityService;
import com.api.generator.config.GenerationJobProperties;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final AccountService accountService;
    private final com.api.generator.security.AuthRateLimiter rateLimiter;
    private final com.api.generator.security.RequestRateLimiter requestRateLimiter;
    private final com.api.generator.security.ClientIpResolver clientIpResolver;
    private final EmailVerificationService emailVerificationService;
    private final com.api.generator.config.EmailVerificationProperties emailVerificationProperties;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final PlanCapabilityService planCapabilityService;
    private final GenerationJobProperties generationJobProperties;

    public AuthController(AuthenticationManager authenticationManager,
                         JwtService jwtService,
                         JwtCookieService jwtCookieService,
                         AccountService accountService,
                         com.api.generator.security.AuthRateLimiter rateLimiter,
                         com.api.generator.security.RequestRateLimiter requestRateLimiter,
                         com.api.generator.security.ClientIpResolver clientIpResolver,
                         EmailVerificationService emailVerificationService,
                         com.api.generator.config.EmailVerificationProperties emailVerificationProperties,
                         ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                         PlanCapabilityService planCapabilityService,
                         GenerationJobProperties generationJobProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.accountService = accountService;
        this.rateLimiter = rateLimiter;
        this.requestRateLimiter = requestRateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.emailVerificationService = emailVerificationService;
        this.emailVerificationProperties = emailVerificationProperties;
        this.clientRegistrationRepository = clientRegistrationRepository.getIfAvailable();
        this.planCapabilityService = planCapabilityService;
        this.generationJobProperties = generationJobProperties;
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   jakarta.servlet.http.HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response) {
        String login = req.resolvedLogin();
        String clientIp = clientIpResolver.resolve(request);

        try {
            if (login == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Missing credentials");
            }

            // SECURITY: Rate limiting to prevent brute-force
            if (rateLimiter.isRateLimited(clientIp) || rateLimiter.isRateLimited(login)) {
                log.warn("Rate limit exceeded for login attempt: {} from {}", login, clientIp);
                throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many login attempts. Please try again later.");
            }

            log.info("Login attempt for '{}'", login);
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login, req.password())
            );

            Object principal = auth.getPrincipal();
            String token;
            String subject;
            List<String> roles;

            if (principal instanceof UserDetails ud) {
                subject = ud.getUsername();
                token = jwtService.generate(ud);
                roles = ud.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
            } else {
                subject = auth.getName();
                UserDetails ud = org.springframework.security.core.userdetails.User
                        .withUsername(subject)
                        .password("")
                        .authorities(auth.getAuthorities())
                        .build();
                token = jwtService.generate(ud);
                roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
            }

            AppUser user = accountService.onLocalLogin(subject);
            log.info("Login success for '{}'", subject);

            // SECURITY: Reset rate limit on successful login
            rateLimiter.reset(clientIp);
            rateLimiter.reset(login);

            jwtCookieService.setAuthCookie(response, token);
            return ResponseEntity.ok(new AuthResponse(subject, user.getPlan(), roles));
        } catch (BadCredentialsException e) {
            log.warn("Login failed (bad credentials) for '{}' from {}", login, clientIp);
            rateLimiter.recordFailure(clientIp);
            rateLimiter.recordFailure(login);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        } catch (DisabledException | IllegalStateException e) {
            log.warn("Login blocked for '{}' from {}: {}", login, clientIp, e.getMessage());
            rateLimiter.recordFailure(clientIp);
            rateLimiter.recordFailure(login);
            throw new ResponseStatusException(FORBIDDEN, "Email not verified");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in /api/auth/login", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (rateLimiter.isRateLimited(clientIp) || !requestRateLimiter.allow("register", clientIp, 5)) {
            log.warn("Register rate limit exceeded from {}", clientIp);
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        }

        try {
            AccountService.RegistrationResult registration = accountService.registerLocal(
                    req.email().trim(),
                    req.password(),
                    emailVerificationProperties.isEnabled(),
                    emailVerificationProperties.getExpirationMinutes()
            );
            AppUser user = registration.user();
            if (emailVerificationProperties.isEnabled() && registration.verificationToken() != null) {
                boolean sent = emailVerificationService.sendVerification(user.getEmail(), registration.verificationToken());
                if (!sent) {
                    throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Account created, but verification email could not be sent. Try again later.");
                }
            }
            log.info("Registered local user '{}'", user.getEmail());

            return ResponseEntity.ok(new EmailVerificationResponse(
                    emailVerificationProperties.isEnabled()
                            ? "Account created. Verify your email before signing in."
                            : "Account created.",
                    user.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in /api/auth/register", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/oauth2/status")
    public ResponseEntity<?> oauth2Status() {
        return ResponseEntity.ok(Map.of("googleEnabled", clientRegistrationRepository != null));
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req,
                                                jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!requestRateLimiter.allow("verify-email-resend", clientIp, 5)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        }

        accountService.createEmailVerificationToken(
                        req.email().trim(),
                        emailVerificationProperties.getExpirationMinutes()
                )
                .ifPresent(registration -> emailVerificationService.sendVerification(
                        registration.user().getEmail(),
                        registration.verificationToken()
                ));

        return ResponseEntity.ok(Map.of(
                "message", "If the account exists and needs verification, a new email has been sent."
        ));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest req,
                                         jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!requestRateLimiter.allow("verify-email", clientIp, 5)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        }
        AppUser user = accountService.verifyEmail(req.token())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired verification token"));
        return ResponseEntity.ok(Map.of(
                "message", "Email verified",
                "email", user.getEmail()
        ));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!requestRateLimiter.allow("password-reset-request", clientIp, 5)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        }
        accountService.createPasswordResetToken(req.email(), emailVerificationProperties.getExpirationMinutes())
                .ifPresent(token -> emailVerificationService.sendPasswordReset(req.email().trim().toLowerCase(), token));
        return ResponseEntity.ok(Map.of("message", "If the account exists, a reset email has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!requestRateLimiter.allow("password-reset-confirm", clientIp, 10)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
        }
        accountService.resetPassword(req.token(), req.password())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired reset code"));
        return ResponseEntity.ok(Map.of("message", "Password reset"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String email = authentication.getName();
        AppUser user = accountService.findByEmailOrNull(email);

        String plan = (user != null && user.getPlan() != null && !user.getPlan().isBlank())
                ? user.getPlan()
                : accountService.defaultPlan();

        List<String> roles = (user != null)
                ? parseRoles(user.getRoles())
                : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        Quotas quotas = toQuotas(user, plan);
        return ResponseEntity.ok(new MeResponse(email, plan, roles, quotas));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletResponse response) {
        jwtCookieService.clearAuthCookie(response);
        return ResponseEntity.noContent().build();
    }

    private List<String> parseRoles(String rawRoles) {
        String source = (rawRoles == null || rawRoles.isBlank()) ? accountService.defaultRoles() : rawRoles;
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Quotas toQuotas(AppUser user, String plan) {
        var capability = user != null
                ? planCapabilityService.capabilityFor(user)
                : planCapabilityService.capabilityFor(plan);
        int used = user == null ? 0 : user.getMonthlyGenerationCount();
        return new Quotas(
                used,
                capability.monthlyGenerationsLimit(),
                user == null ? 0 : user.getMonthlyDockerDeploymentCount(),
                capability.monthlyDockerDeploymentsLimit(),
                user == null ? 0 : user.getMonthlyZipDownloadCount(),
                capability.monthlyZipDownloadsLimit(),
                capability.canBuild(),
                generationJobProperties.isDockerRequestEnabled()
                        && user != null
                        && planCapabilityService.canDeployDocker(user),
                true
        );
    }
}
