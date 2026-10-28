package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.dao.AssistConfigDao;
import com.aiyi.game.dnfserver.entity.AssistConfig;
import com.aiyi.game.dnfserver.service.impl.AssistConfigServiceImpl;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AssistReadOnlyTest {
    private AssistConfigDao dao;
    private AssistConfigServiceImpl service;
    @Before public void setup(){
        dao=mock(AssistConfigDao.class);service=new AssistConfigServiceImpl();
        ReflectionTestUtils.setField(service,"assistConfigDao",dao);
    }
    @Test public void absentConfigReturnsIndependentDefaultsWithoutInserting(){
        java.util.Map<String,Object> first=service.getConfig();first.put("角色等级上限",-1);
        assertNotEquals(-1,service.getConfig().get("角色等级上限"));
        verify(dao,times(2)).get(1);verifyNoMoreInteractions(dao);
    }
    @Test public void publicTextReadDoesNotInitializeDatabase(){
        assertTrue(service.getConfigText().contains("角色等级上限"));
        verify(dao).get(1);verifyNoMoreInteractions(dao);
    }
    @Test public void existingCustomKeysAreReturnedWithoutWritingDefaults(){
        AssistConfig cfg=new AssistConfig();cfg.setConfigJson("{\"custom-extension\":42,\"角色等级上限\":86}");
        when(dao.get(1)).thenReturn(cfg);
        assertEquals(42,service.getConfig().get("custom-extension"));
        verify(dao).get(1);verifyNoMoreInteractions(dao);
    }
}
