package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import com.aiyi.game.dnfserver.service.extension.ExtensionQueryService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.Assert.*;

public class LauncherRuntimeTest {
    private static class Security extends ExtensionQueryService {
        boolean admin;
        @Override public void requireAdmin() {
            if (!admin) throw new ActivityConfigService.Failure(403, "admin required");
        }
    }

    private ClientLauncherAdminController controller(String mode, boolean admin) {
        ClientLauncherAdminController controller = new ClientLauncherAdminController();
        Security security = new Security();
        security.admin = admin;
        ReflectionTestUtils.setField(controller, "security", security);
        ReflectionTestUtils.setField(controller, "launcherMode", mode);
        return controller;
    }

    @Test public void reportsBothModesWithoutSecretsOrDatabaseWrites() {
        for (String mode : new String[]{"dof", "qingkong"}) {
            Map<String, Object> result = controller(mode, true).runtime();
            assertEquals(mode, result.get("mode"));
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("mode", "configurationOwner", "qingkongDevelopment")), result.keySet());
            assertEquals(false, result.get("qingkongDevelopment"));
            assertEquals("qingkong".equals(mode) ? "qingkong-tool" : "web-admin", result.get("configurationOwner"));
        }
    }

    @Test(expected = ActivityConfigService.Failure.class)
    public void rejectsNonAdmin() { controller("qingkong", false).runtime(); }

    @Test(expected = IllegalStateException.class)
    public void rejectsInvalidModeInsteadOfShowingDof() { controller("typo", true).runtime(); }
}
