package com.api.generator.account.api;

import com.api.generator.account.AppUser;
import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.PreviewStatus;
import com.api.generator.account.repo.ApiPreviewRepository;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.account.service.AccountService;
import com.api.generator.account.service.PlanCapabilityService;
import com.api.generator.api.service.GenerationService;
import com.api.generator.api.service.PreviewRuntimeService;
import com.api.generator.api.service.PreviewService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class AccountControllerPreviewDiagnosticsApiTest {

    private ApiPreviewRepository previewRepo;
    private MockMvc mockMvc;
    private GeneratedApi generatedApi;
    private AppUser user;

    @BeforeEach
    void setUp() throws Exception {
        AppUserRepository users = mock(AppUserRepository.class);
        GenerationService generations = mock(GenerationService.class);
        PreviewService previews = mock(PreviewService.class);
        previewRepo = mock(ApiPreviewRepository.class);
        AccountService accountService = mock(AccountService.class);
        PlanCapabilityService planCapabilityService = mock(PlanCapabilityService.class);

        AccountController controller = new AccountController(
                users,
                generations,
                previews,
                previewRepo,
                accountService,
                planCapabilityService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        user = new AppUser();
        setAppUserId(user, UUID.randomUUID());
        user.setEmail("tester@example.com");
        when(users.findByEmailIgnoreCase("tester@example.com")).thenReturn(Optional.of(user));

        generatedApi = new GeneratedApi();
        setGeneratedApiId(generatedApi, UUID.randomUUID());
        generatedApi.setName("DiagnosticsApi");
        generatedApi.setStatus(GenerationStatus.DONE);

        when(generations.requireOwned(generatedApi.getId(), user)).thenReturn(generatedApi);
        when(previews.diagnostics(generatedApi)).thenReturn(new PreviewService.PreviewDiagnostics(
                "DONE",
                "FAILED",
                true,
                true,
                true,
                false,
                "docker",
                List.of(
                        new PreviewRuntimeService.HostCheck("containerRuntimeBinary", true, "binary ok"),
                        new PreviewRuntimeService.HostCheck("containerRuntimeReachable", false, "runtime unreachable")
                ),
                new PreviewService.Recommendation("FIX_HOST_DIAGNOSTICS", "Fix the failing host checks before launching preview.")
        ));
    }

    @Test
    void previewDiagnosticsEndpointReturnsStructuredJson() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("tester@example.com", "n/a");

        mockMvc.perform(get("/api/account/apis/{id}/preview/diagnostics", generatedApi.getId())
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationStatus").value("DONE"))
                .andExpect(jsonPath("$.previewStatus").value("FAILED"))
                .andExpect(jsonPath("$.hostReady").value(false))
                .andExpect(jsonPath("$.containerRuntime").value("docker"))
                .andExpect(jsonPath("$.hostChecks[1].key").value("containerRuntimeReachable"))
                .andExpect(jsonPath("$.recommendedAction.code").value("FIX_HOST_DIAGNOSTICS"));
    }

    @Test
    void failedPreviewsEndpointReturnsRecentFailures() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("tester@example.com", "n/a");

        ApiPreview failedPreview = new ApiPreview();
        failedPreview.setGeneratedApi(generatedApi);
        failedPreview.setStatus(PreviewStatus.FAILED);
        failedPreview.setErrorCode("PREVIEW_BUILD_FAILED");
        failedPreview.setErrorMessage("Command failed (1): mvn clean package");
        failedPreview.setErrorHint("Inspect preview logs and Maven artifacts.");
        failedPreview.setStoppedAt(java.time.Instant.parse("2026-04-23T18:00:00Z"));

        when(previewRepo.findByGeneratedApi_User_IdAndStatusOrderByStoppedAtDesc(
                user.getId(),
                PreviewStatus.FAILED,
                org.springframework.data.domain.PageRequest.of(0, 5)
        )).thenReturn(List.of(failedPreview));

        mockMvc.perform(get("/api/account/previews/failed")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].generatedApiId").value(generatedApi.getId().toString()))
                .andExpect(jsonPath("$[0].generatedApiName").value("DiagnosticsApi"))
                .andExpect(jsonPath("$[0].errorCode").value("PREVIEW_BUILD_FAILED"));
    }

    private void setGeneratedApiId(GeneratedApi api, UUID id) throws Exception {
        Field field = GeneratedApi.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(api, id);
    }

    private void setAppUserId(AppUser appUser, UUID id) throws Exception {
        Field field = AppUser.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(appUser, id);
    }
}
