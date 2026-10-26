package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;

public class DofClientReadinessTest {
    private static DofClientService service(Path workspace) throws Exception {
        DofAdminService guard = new DofAdminService() {
            @Override long admin() { return 42; }
            @Override Connection connection() {
                return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) return null;
                        throw new UnsupportedOperationException("Status must not execute SQL");
                    });
            }
            @Override Map<String,Object> context() {
                Map<String,Object> value = new LinkedHashMap<>(); value.put("instanceId", "dof-main"); return value;
            }
        };
        DofClientService service = new DofClientService();
        Field field = DofClientService.class.getDeclaredField("adminService"); field.setAccessible(true); field.set(service, guard);
        field = DofClientService.class.getDeclaredField("workspace"); field.setAccessible(true); field.set(service, workspace.toString());
        return service;
    }
    @Test public void missingInputsHaveClearStatusWithoutCreatingDirectories() throws Exception {
        Path root = Files.createTempDirectory("dof-client-readiness-");
        try {
            Path workspace = root.resolve("not-imported"); DofClientService service = service(workspace);
            for (Object value : Arrays.asList(service.status(), service.scriptStatus())) {
                Map<?,?> status = (Map<?,?>)value;
                assertEquals(false, status.get("enabled")); assertEquals("dof-main", status.get("instanceId"));
                assertNotNull(status.get("blocker"));
            }
            try { service.submit(new NativeClientJobs.Request()); fail(); }
            catch (DofFailure expected) { assertEquals(409, expected.status); }
            assertFalse(Files.exists(workspace));
        } finally { Files.delete(root); }
    }
    @Test public void linkedInputDirectoryIsRejectedBeforeOpeningFiles() throws Exception {
        Path root = Files.createTempDirectory("dof-client-readiness-link-");
        Path inputs = root.resolve("inputs");
        try {
            Files.createSymbolicLink(inputs, root.resolve("private-other-instance"));
            try { service(root).status(); fail(); }
            catch (DofFailure expected) { assertEquals(409, expected.status); }
        } finally { Files.deleteIfExists(inputs); Files.delete(root); }
    }
}
