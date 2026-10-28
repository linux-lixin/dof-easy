package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.DofAdminController;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.Connection;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class DofPluginHttpTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void permissionAndBindingPrecedeAnyFileAccess()throws Exception {
        DofAdminService access=mock(DofAdminService.class);DofPluginService service=new DofPluginService();
        ReflectionTestUtils.setField(service,"access",access);
        DofAdminController controller=new DofAdminController();ReflectionTestUtils.setField(controller,"plugins",service);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).build();
        when(access.admin()).thenThrow(new DofFailure(403,"admin required"));
        mvc.perform(get("/api/v1/admin/dof/plugins")).andExpect(status().isForbidden());verify(access,never()).boundConnection();
        String revision=String.join("",Collections.nCopies(64,"0"));
        String request="{\"requestId\":\"runtime-test-01\",\"revision\":\""+revision+"\"}";
        for(String suffix:Arrays.asList("/runtime/preview","/runtime","/runtime/runtime-test-01/reconcile"))
            mvc.perform(post("/api/v1/admin/dof/plugins/anton"+suffix).contentType("application/json").content(request)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dof/plugins/anton/runtime/history")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/dof/plugins/anton/runtime/runtime-test-01/resolve").contentType("application/json")
            .content("{\"requestId\":\"runtime-resolve-01\",\"taskRevision\":\""+revision+"\",\"keepCurrent\":true}")).andExpect(status().isForbidden());
        verify(access,never()).boundConnection();
        reset(access);when(access.admin()).thenReturn(1L);when(access.boundConnection()).thenThrow(new DofFailure(409,"binding mismatch"));
        mvc.perform(get("/api/v1/admin/dof/plugins/anton")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/dof/plugins/anton/runtime/preview").contentType("application/json").content(request)).andExpect(status().isConflict());
    }
    @Test public void missingMountIsReportedAndDofModeDoesNotHidePluginCatalog()throws Exception {
        DofAdminService access=mock(DofAdminService.class);when(access.admin()).thenReturn(1L);when(access.boundConnection()).thenReturn(mock(Connection.class));
        when(access.context()).thenAnswer(i->new LinkedHashMap<String,Object>(Collections.singletonMap("instanceId","dof-test")));
        DofPluginService service=new DofPluginService();ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"launcherMode","dof");ReflectionTestUtils.setField(service,"pluginMode","dp2");ReflectionTestUtils.setField(service,"raidExtension","on");
        DofAdminController controller=new DofAdminController();ReflectionTestUtils.setField(controller,"plugins",service);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/admin/dof/plugins")).andExpect(status().isOk())
            .andExpect(jsonPath("$.launcherMode").value("dof")).andExpect(jsonPath("$.modules[1].dependencyReady").value(true))
            .andExpect(jsonPath("$.modules[1].available").value(false));
        mvc.perform(get("/api/v1/admin/dof/plugins/anton")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/dof/plugins/../../etc/passwd")).andExpect(status().isNotFound());
    }
    @Test public void fileDirectoryMustMatchBothBoundDatabaseAndInstance()throws Exception {
        Path root=temporary.newFolder("config").toPath();byte[] original="Inlay_function = 0\n".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("plugin.ini"),original);
        Path marker=root.resolve(".admin-instance.json");
        Files.write(marker,"{\"instanceId\":\"dof-test\",\"databaseUuid\":\"db-one\",\"environment\":\"isolated\"}".getBytes(StandardCharsets.UTF_8));
        DofAdminService access=mock(DofAdminService.class);when(access.admin()).thenReturn(1L);when(access.boundConnection()).thenReturn(mock(Connection.class));
        when(access.boundDatabaseUuid()).thenReturn("db-one");
        when(access.context()).thenAnswer(i->new LinkedHashMap<String,Object>(Collections.singletonMap("instanceId","dof-test")));
        DofPluginService service=new DofPluginService();ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"configRoot",root.toString());ReflectionTestUtils.setField(service,"auditRoot",temporary.getRoot().toPath().resolve("audit").toString());
        assertNotNull(((Map<?,?>)service.read("plugin")).get("revision"));
        Files.write(marker,"{\"instanceId\":\"dof-other\",\"databaseUuid\":\"db-one\",\"environment\":\"isolated\"}".getBytes(StandardCharsets.UTF_8));
        try{service.read("plugin");fail();}catch(DofFailure e){assertEquals(409,e.status);}
        Files.write(marker,"{\"instanceId\":\"dof-test\",\"databaseUuid\":\"db-other\",\"environment\":\"isolated\"}".getBytes(StandardCharsets.UTF_8));
        try{service.read("plugin");fail();}catch(DofFailure e){assertEquals(409,e.status);}
        assertArrayEquals(original,Files.readAllBytes(root.resolve("plugin.ini")));
    }
    @Test public void raidGuideReadsCurrentCommandWithoutClaimingRuntimeApplication()throws Exception {
        Path root=temporary.newFolder("raid-config").toPath();
        Files.write(root.resolve("anton-raid.ini"),"anton_reload = 测试刷新命令\nanton_member_min = 1\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve(".admin-instance.json"),"{\"instanceId\":\"dof-test\",\"databaseUuid\":\"db-one\",\"environment\":\"isolated\"}".getBytes(StandardCharsets.UTF_8));
        DofAdminService access=mock(DofAdminService.class);when(access.admin()).thenReturn(1L);when(access.boundConnection()).thenReturn(mock(Connection.class));
        when(access.boundDatabaseUuid()).thenReturn("db-one");when(access.context()).thenAnswer(i->new LinkedHashMap<String,Object>(Collections.singletonMap("instanceId","dof-test")));
        DofPluginService service=new DofPluginService();ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"configRoot",root.toString());ReflectionTestUtils.setField(service,"auditRoot",temporary.getRoot().toPath().resolve("raid-audit").toString());
        ReflectionTestUtils.setField(service,"pluginMode","dp2-frida");ReflectionTestUtils.setField(service,"raidExtension","on");
        Map<?,?> guide=(Map<?,?>)((Map<?,?>)service.read("anton")).get("application");
        assertEquals("gm_chat",guide.get("method"));assertEquals("测试刷新命令",guide.get("command"));
        assertEquals(false,guide.get("webExecutionSupported"));assertEquals(false,guide.get("runtimeObserved"));assertEquals(true,guide.get("dependencyReady"));
        Files.write(root.resolve("anton-raid.ini"),"anton_member_min = 1\n".getBytes(StandardCharsets.UTF_8));
        guide=(Map<?,?>)((Map<?,?>)service.read("anton")).get("application");assertEquals("unavailable",guide.get("method"));assertNull(guide.get("command"));
    }
}
