package com.api.generator.security.api;

import com.api.generator.account.AppUser;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.api.service.GenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManagedApiSecurityDeploymentControllerTest {

    @Test
    void returnsOnlyRunningDeploymentsOwnedByAuthenticatedUser() throws Exception {
        AppUserRepository users = mock(AppUserRepository.class);
        GenerationService generations = mock(GenerationService.class);
        AppUser owner = user("owner@example.com");
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));

        when(generations.findByUser(owner)).thenReturn(List.of(
                deployedApi("Live API", "http://127.0.0.1:8081"),
                deployedApi("Undeployed API", null)
        ));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ManagedApiSecurityDeploymentController(users, generations)
        ).build();

        mvc.perform(get("/api/security/deployments")
                        .principal(new UsernamePasswordAuthenticationToken(owner.getEmail(), "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Live API"))
                .andExpect(jsonPath("$[0].status").value("DEPLOYED"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private static AppUser user(String email) throws Exception {
        AppUser user = new AppUser();
        setField(user, "id", UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    private static GeneratedApi deployedApi(String name, String apiBaseUrl) throws Exception {
        GeneratedApi api = new GeneratedApi();
        setField(api, "id", UUID.randomUUID());
        api.setName(name);
        api.setApiBaseUrl(apiBaseUrl);
        return api;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
