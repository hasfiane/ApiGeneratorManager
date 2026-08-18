package com.api.generator.account.service;

import com.api.generator.account.AppUser;
import com.api.generator.config.AccountProperties;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PlanCapabilityService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Capability UNLIMITED_CAPABILITY = new Capability(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);

    private final AccountProperties accountProperties;

    public PlanCapabilityService(AccountProperties accountProperties) {
        this.accountProperties = accountProperties;
    }

    public Capability capabilityFor(String rawPlan) {
        String plan = normalize(rawPlan);
        return switch (plan) {
            case "TEAM" -> new Capability(500, 500, 500, true);
            case "PRO" -> new Capability(100, 100, 100, true);
            default -> new Capability(5, 1, 1, true);
        };
    }

    public Capability capabilityFor(AppUser user) {
        if (hasUnlimitedAdminQuotas(user)) {
            return UNLIMITED_CAPABILITY;
        }
        return capabilityFor(user == null ? null : user.getPlan());
    }

    public void ensureCanStartGeneration(AppUser user, boolean build, boolean deployDocker) {
        Capability capability = capabilityFor(user);
        rollPeriodIfNeeded(user);
        if (user.getMonthlyGenerationCount() >= capability.monthlyGenerationsLimit()) {
            throw new PlanLimitExceededException("Monthly generation quota exceeded");
        }
        if (build && !capability.canBuild()) {
            throw new PlanLimitExceededException("Build is not available for your plan");
        }
        if (deployDocker && !canDeployDocker(user)) {
            throw new PlanLimitExceededException("Monthly Docker deployment quota exceeded");
        }
    }

    public void ensureCanDownloadZip(AppUser user) {
        rollPeriodIfNeeded(user);
        Capability capability = capabilityFor(user);
        if (user == null || user.getMonthlyZipDownloadCount() >= capability.monthlyZipDownloadsLimit()) {
            throw new PlanLimitExceededException("Monthly ZIP download quota exceeded");
        }
    }

    public void markGenerationStarted(AppUser user) {
        rollPeriodIfNeeded(user);
        user.setMonthlyGenerationCount(user.getMonthlyGenerationCount() + 1);
    }

    public void markDockerDeploymentSucceeded(AppUser user) {
        rollPeriodIfNeeded(user);
        user.setMonthlyDockerDeploymentCount(user.getMonthlyDockerDeploymentCount() + 1);
    }

    public void markZipDownloaded(AppUser user) {
        rollPeriodIfNeeded(user);
        user.setMonthlyZipDownloadCount(user.getMonthlyZipDownloadCount() + 1);
    }

    public void rollPeriodIfNeeded(AppUser user) {
        if (user == null) {
            return;
        }
        String current = YearMonth.now().format(PERIOD_FORMAT);
        if (!current.equals(user.getMonthlyGenerationPeriod())) {
            user.setMonthlyGenerationPeriod(current);
            user.setMonthlyGenerationCount(0);
            user.setMonthlyDockerDeploymentCount(0);
            user.setMonthlyZipDownloadCount(0);
        }
    }

    public boolean canDeployDocker(AppUser user) {
        if (user == null) {
            return false;
        }
        rollPeriodIfNeeded(user);
        Capability capability = capabilityFor(user);
        return user.getMonthlyDockerDeploymentCount() < capability.monthlyDockerDeploymentsLimit();
    }

    public boolean canDownloadZip(AppUser user) {
        if (user == null) {
            return false;
        }
        rollPeriodIfNeeded(user);
        Capability capability = capabilityFor(user);
        return user.getMonthlyZipDownloadCount() < capability.monthlyZipDownloadsLimit();
    }

    private String normalize(String rawPlan) {
        if (rawPlan == null || rawPlan.isBlank()) {
            return "FREE";
        }
        return rawPlan.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasUnlimitedAdminQuotas(AppUser user) {
        if (!accountProperties.isAdminUnlimitedQuotas() || user == null) {
            return false;
        }
        String roles = user.getRoles();
        if (roles == null || roles.isBlank()) {
            return false;
        }
        for (String role : roles.split(",")) {
            if ("ROLE_ADMIN".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }

    public record Capability(
            int monthlyGenerationsLimit,
            int monthlyDockerDeploymentsLimit,
            int monthlyZipDownloadsLimit,
            boolean canBuild
    ) {}

    public static class PlanLimitExceededException extends RuntimeException {
        public PlanLimitExceededException(String message) {
            super(message);
        }
    }
}
