package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.CharacInfoController;
import com.aiyi.game.dnfserver.controller.ExtensionFailureAdvice;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.impl.CharacServiceImpl;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import com.aiyi.game.dnfserver.service.extension.ExtensionQueryService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class QuestRetirementHttpTest {
    private MockMvc http;
    private CharacServiceImpl service;
    private CharacInfoDao dao;
    private PvfManager pvf;
    private ExtensionQueryService security;
    @Before public void setup() {
        service=new CharacServiceImpl();dao=mock(CharacInfoDao.class);
        pvf=mock(PvfManager.class);security=mock(ExtensionQueryService.class);
        ReflectionTestUtils.setField(service,"characInfoDao",dao);
        ReflectionTestUtils.setField(service,"pvfManager",pvf);
        ReflectionTestUtils.setField(service,"managementSecurity",security);
        CharacInfoController controller=new CharacInfoController();
        ReflectionTestUtils.setField(controller,"characService",service);
        http=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExtensionFailureAdvice()).build();
    }
    @Test public void repeatedLegacyRequestsNeverReadOrChangeTasks() throws Exception {
        for(int i=0;i<2;i++) http.perform(post("/api/v1/charac/6/overTasks"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.message").isNotEmpty());
        verify(security,times(2)).requireAdmin();verifyZeroInteractions(dao,pvf);
    }
    @Test public void InventedReceiptDoesNotReactivateTheLoop() throws Exception {
        http.perform(post("/api/v1/charac/6/overTasks").contentType("application/json")
                .content("{\"requestId\":\"00000000-0000-4000-8000-000000000051\",\"revision\":\"invented\"}"))
                .andExpect(status().isGone());
        verifyZeroInteractions(dao,pvf);
    }
    @Test public void nonAdminRejectedBeforeAnyTaskRead() throws Exception {
        doThrow(new ActivityConfigService.Failure(403,"管理员权限不足")).when(security).requireAdmin();
        http.perform(post("/api/v1/charac/6/overTasks")).andExpect(status().isForbidden());
        verifyZeroInteractions(dao,pvf);
    }
    @Test public void internalCallsAlsoCannotReachOldTaskOrMailWrites() {
        try {service.overTasks(6L);fail("Legacy execution must be unavailable");}
        catch(DofFailure e){assertEquals(410,e.status);}
        verify(security).requireAdmin();verifyZeroInteractions(dao,pvf);
    }
}
