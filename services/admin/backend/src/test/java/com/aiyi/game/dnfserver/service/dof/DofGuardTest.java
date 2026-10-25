package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DofGuardTest {
    private DofAdminService service;
    private AccountVODao accounts;
    private DataSource database;
    @Before public void setup() {
        ThreadUtil.setUserId(1L); service = new DofAdminService();
        accounts = mock(AccountVODao.class); database = mock(DataSource.class);
        ReflectionTestUtils.setField(service, "accountVODao", accounts); ReflectionTestUtils.setField(service, "dataSource", database);
        AccountVO admin = new AccountVO(); admin.setAdmin(true); when(accounts.get(1L)).thenReturn(admin);
    }
    private void rejects(int status, Runnable action) {
        try { action.run(); fail(); } catch (DofFailure e) { assertEquals(status, e.status); }
    }
    @Test public void authorizationPrecedesAnyDatabaseRead() {
        when(accounts.get(1L)).thenReturn(null);
        rejects(403, () -> service.catalog()); rejects(403, () -> service.ui()); rejects(403, () -> service.history());
        rejects(403, () -> service.save(null, null)); rejects(403, () -> service.reconcile("invalid")); verifyZeroInteractions(database);
        rejects(403, () -> service.presetImage("creation-100"));
        rejects(403, () -> service.presetImage("native-100")); verifyZeroInteractions(database);
    }
    @Test public void disabledByDefaultAndRejectsProductionEvenWhenEnabled() {
        rejects(409, () -> service.ui());
        ReflectionTestUtils.setField(service, "enabled", true); ReflectionTestUtils.setField(service, "environment", "production");
        rejects(409, () -> service.ui()); verifyZeroInteractions(database);
        rejects(409, () -> service.presetImage("creation-100")); verifyZeroInteractions(database);
    }
    @Test public void rejectsIncompleteBindingAndMalformedRequestsBeforeConnecting() {
        ReflectionTestUtils.setField(service, "enabled", true); ReflectionTestUtils.setField(service, "environment", "isolated");
        rejects(409, () -> service.ui()); rejects(400, () -> service.save(null, null));
        DofAdminService.Edit edit = new DofAdminService.Edit(); edit.revision = "invalid";
        rejects(400, () -> service.preview(edit)); verifyZeroInteractions(database);
    }
    @Test public void startupBindingIsOptInAndRejectsMisconfiguredRuntime() {
        service.verifyStartupBinding(); verifyZeroInteractions(database);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "environment", "production");
        rejects(409, () -> service.verifyStartupBinding()); verifyZeroInteractions(database);
    }
    @Test public void rejectsLegacyAdapterBeforeAnyDatabaseAccess() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "environment", "isolated");
        ReflectionTestUtils.setField(service, "instanceId", "dof-contract");
        ReflectionTestUtils.setField(service, "databaseUuid", "12345678-1234-4234-8234-123456789abc");
        ReflectionTestUtils.setField(service, "adapterVersion", "qingkong-2.8-current-20261024");
        rejects(409, () -> service.verifyStartupBinding()); verifyZeroInteractions(database);
    }
}
