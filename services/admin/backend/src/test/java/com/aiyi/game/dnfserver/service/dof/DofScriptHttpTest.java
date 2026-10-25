package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.DofAdminController;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DofScriptHttpTest {
    private MockMvc mvc(DofClientService clients,DofScriptReleaseService releases) {
        DofAdminController c=new DofAdminController();ReflectionTestUtils.setField(c,"clientService",clients);ReflectionTestUtils.setField(c,"scriptReleases",releases);
        return MockMvcBuilders.standaloneSetup(c).build();
    }
    @Test public void uploadStreamsRawBytesAndUsesDeclaredLength()throws Exception {
        DofClientService clients=mock(DofClientService.class);String id=UUID.randomUUID().toString();byte[] source=NativeScriptJobsTest.fixture(1);
        when(clients.uploadScript(eq(id),eq(NativeScriptJobsTest.REV),eq((long)source.length),any(InputStream.class))).thenAnswer(call->{byte[] b=new byte[source.length];new DataInputStream(call.getArgument(3)).readFully(b);assertArrayEquals(source,b);return Collections.singletonMap("state","READY");});
        MvcResult result=mvc(clients,null).perform(post("/api/v1/admin/dof/script/uploads").param("requestId",id).param("revision",NativeScriptJobsTest.REV).contentType("application/octet-stream").content(source)).andReturn();
        assertEquals(200,result.getResponse().getStatus());verify(clients).uploadScript(eq(id),eq(NativeScriptJobsTest.REV),eq((long)source.length),any(InputStream.class));
    }
    @Test public void capacityAndVersionRejectionsRemainHttpErrors()throws Exception {
        DofClientService clients=mock(DofClientService.class);when(clients.submitScript(any())).thenThrow(new DofFailure(507,"space"));
        assertEquals(507,mvc(clients,null).perform(post("/api/v1/admin/dof/script/tasks").contentType("application/json").content("{}" )).andReturn().getResponse().getStatus());
        DofScriptReleaseService releases=mock(DofScriptReleaseService.class);when(releases.apply(any(),isNull())).thenThrow(new DofFailure(409,"version"));
        assertEquals(409,mvc(clients,releases).perform(post("/api/v1/admin/dof/client/releases").contentType("application/json").content("{}" )).andReturn().getResponse().getStatus());
    }
    @Test public void wrongDatabaseCannotReadOrCreateScriptWorkspace()throws Exception {
        DofAdminService guard=mock(DofAdminService.class);when(guard.admin()).thenReturn(42L);when(guard.connection()).thenThrow(new DofFailure(409,"wrong-instance"));
        DofClientService clients=new DofClientService();ReflectionTestUtils.setField(clients,"adminService",guard);ReflectionTestUtils.setField(clients,"workspace","/nonexistent/must-not-open");
        MockMvc mvc=mvc(clients,null);
        for(String path:Arrays.asList("script","script/uploads","script/tasks"))assertEquals(409,mvc.perform(get("/api/v1/admin/dof/"+path)).andReturn().getResponse().getStatus());
        assertEquals(409,mvc.perform(post("/api/v1/admin/dof/script/uploads").param("requestId",UUID.randomUUID().toString()).param("revision",NativeScriptJobsTest.REV).contentType("application/octet-stream").content(NativeScriptJobsTest.fixture(1))).andReturn().getResponse().getStatus());
    }
    @Test public void noAdministratorCannotReadReleaseHistoryOrStartUpload()throws Exception {
        DofAdminService guard=mock(DofAdminService.class);when(guard.admin()).thenThrow(new DofFailure(403,"admin-required"));
        DofClientService clients=new DofClientService();ReflectionTestUtils.setField(clients,"adminService",guard);
        DofScriptReleaseService releases=new DofScriptReleaseService();ReflectionTestUtils.setField(releases,"access",guard);
        MockMvc mvc=mvc(clients,releases);
        assertEquals(403,mvc.perform(get("/api/v1/admin/dof/client/releases")).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/v1/admin/dof/script/uploads").param("requestId",UUID.randomUUID().toString()).param("revision",NativeScriptJobsTest.REV).contentType("application/octet-stream").content(NativeScriptJobsTest.fixture(1))).andReturn().getResponse().getStatus());
        verify(guard,never()).connection();
    }
}
