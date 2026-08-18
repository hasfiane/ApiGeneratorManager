package com.api.generator.account.service;

import com.api.generator.account.AppUser;
import com.api.generator.config.AccountProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCapabilityServiceTest {

    private final PlanCapabilityService service = new PlanCapabilityService(new AccountProperties());

    @Test
    void freeBetaAllowsOneZipDownload() {
        AppUser user = new AppUser();
        user.setPlan("FREE");

        assertTrue(service.canDownloadZip(user));
        assertDoesNotThrow(() -> service.ensureCanDownloadZip(user));

        service.markZipDownloaded(user);

        assertFalse(service.canDownloadZip(user));
        assertThrows(PlanCapabilityService.PlanLimitExceededException.class,
                () -> service.ensureCanDownloadZip(user));
    }
}
