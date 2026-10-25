package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;

public class DofClientGuardTest {
    private static DofClientService service(DofAdminService guard, String workspace) throws Exception {
        DofClientService service=new DofClientService();
        Field field=DofClientService.class.getDeclaredField("adminService");field.setAccessible(true);field.set(service,guard);
        field=DofClientService.class.getDeclaredField("workspace");field.setAccessible(true);field.set(service,workspace);
        return service;
    }
    @Test public void disabledPublicationStillRequiresAdministratorAndIsolatedDatabase() throws Exception {
        DofAdminService guard=mock(DofAdminService.class);when(guard.admin()).thenReturn(42L);
        Connection connection=mock(Connection.class);when(guard.connection()).thenReturn(connection);
        when(guard.context()).thenReturn(new LinkedHashMap<String,Object>());
        assertEquals(false,((Map<?,?>)service(guard,"").status()).get("enabled"));
        verify(guard).admin();verify(guard).connection();verify(connection).close();
    }
    @Test public void unauthorizedRequestsCannotOpenWorkspace() throws Exception {
        DofAdminService guard=mock(DofAdminService.class);when(guard.admin()).thenThrow(new DofFailure(403,"denied"));
        DofClientService service=service(guard,"/path/must/not/be/opened");
        try{service.status();fail();}catch(DofFailure e){assertEquals(403,e.status);}
        verify(guard,never()).connection();
    }
    @Test public void misboundDatabaseBlocksStatusSubmissionReadAndDownload() throws Exception {
        DofAdminService guard=mock(DofAdminService.class);when(guard.admin()).thenReturn(42L);
        when(guard.connection()).thenThrow(new DofFailure(409,"wrong identity"));
        Path base=Files.createTempDirectory("dof_native-client-guard-");Path workspace=base.resolve("must-not-exist");
        try {
            DofClientService service=service(guard,workspace.toString());
            for(int i=0;i<5;i++) {
                try {
                    if(i==0)service.status();else if(i==1)service.submit(new NativeClientJobs.Request());else if(i==2)service.list();else if(i==3)service.read(UUID.randomUUID().toString());else service.download(UUID.randomUUID().toString());
                    fail();
                } catch(DofFailure e){assertEquals(409,e.status);}
            }
            assertFalse(Files.exists(workspace));
        } finally { Files.delete(base); }
    }
}
