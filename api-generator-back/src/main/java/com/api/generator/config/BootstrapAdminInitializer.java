package com.api.generator.config;

import com.api.generator.account.AppUser;
import com.api.generator.account.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    @Bean
    ApplicationRunner bootstrapAdminRunner(AppUserRepository users,
                                           PasswordEncoder passwordEncoder,
                                           AccountProperties accountProperties,
                                           Environment environment) {
        return args -> {
            if (!accountProperties.isBootstrapEnabled()) {
                return;
            }

            boolean localProfileActive = environment.acceptsProfiles(Profiles.of("local"));
            String login = normalize(accountProperties.getBootstrapEmail());

            AppUser user = users.findByEmailIgnoreCase(login).orElseGet(AppUser::new);
            boolean created = user.getId() == null;

            user.setEmail(login);
            user.setProvider(AppUser.Provider.LOCAL);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setEmailVerificationTokenHash(null);
            user.setEmailVerificationExpiresAt(null);
            user.setRoles(accountProperties.getBootstrapRoles());
            user.setPlan(accountProperties.getBootstrapPlan());

            boolean shouldResetPassword = created || accountProperties.isBootstrapResetPasswordOnStartup()
                    || user.getPasswordHash() == null || user.getPasswordHash().isBlank();
            if (shouldResetPassword) {
                user.setPasswordHash(passwordEncoder.encode(accountProperties.getBootstrapPassword()));
            }
            if (localProfileActive) {
                user.setMonthlyGenerationCount(0);
            }

            users.save(user);

            if (created) {
                log.warn("Bootstrap admin created: {}", login);
            } else if (shouldResetPassword) {
                log.warn("Bootstrap admin password reset on startup: {}", login);
            } else {
                log.info("Bootstrap admin present: {}", login);
            }
        };
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }
}
