package com.api.generator.security.api;

import com.api.generator.account.AppUser;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.api.service.GenerationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lists the caller's Docker-deployed APIs. A Docker deployment is persisted as
 * a completed GeneratedApi with a non-empty base URL; it is not an ApiPreview.
 */
@RestController
@RequestMapping(path = "/api/security/deployments", produces = MediaType.APPLICATION_JSON_VALUE)
public class ManagedApiSecurityDeploymentController {

    private final AppUserRepository users;
    private final GenerationService generations;

    public ManagedApiSecurityDeploymentController(AppUserRepository users, GenerationService generations) {
        this.users = users;
        this.generations = generations;
    }

    @GetMapping
    public List<SecurityDeploymentView> deployments(Authentication authentication) {
        AppUser user = requireUser(authentication);
        return generations.findByUser(user).stream()
                .filter(api -> api.getApiBaseUrl() != null && !api.getApiBaseUrl().isBlank())
                .map(SecurityDeploymentView::from)
                .toList();
    }

    private AppUser requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
    }

    public record SecurityDeploymentView(UUID id, String name, String status) {
        static SecurityDeploymentView from(com.api.generator.account.GeneratedApi api) {
            return new SecurityDeploymentView(
                    api.getId(),
                    api.getName(),
                    "DEPLOYED"
            );
        }
    }
}
