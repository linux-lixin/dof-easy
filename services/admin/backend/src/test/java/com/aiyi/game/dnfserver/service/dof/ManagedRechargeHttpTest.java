package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.RechargeController;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ManagedRechargeHttpTest {
    private MockMvc http;private ManagedOperationJournal journal;
    @Before public void setup(){
        journal=mock(ManagedOperationJournal.class);when(journal.admin()).thenReturn(1L);
        ManagedRechargeService service=new ManagedRechargeService();ReflectionTestUtils.setField(service,"journal",journal);
        RechargeController controller=new RechargeController();ReflectionTestUtils.setField(controller,"recharge",service);
        http=MockMvcBuilders.standaloneSetup(controller).build();
    }
    private String body(String uid,String amount,String operation){return "{\"uid\":"+uid+",\"cera\":"+amount+",\"operation\":\""+operation+"\",\"requestId\":\"00000000-0000-4000-8000-000000000001\",\"revision\":\""+String.join("",Collections.nCopies(64,"a"))+"\",\"reason\":\"synthetic reason\"}";}
    @Test public void oldUnversionedWriteIsExplicitlyRejected()throws Exception{
        http.perform(post("/api/v1/recharge").contentType("application/json").content("{\"uid\":22,\"cera\":5}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());verify(journal,never()).connection();
    }
    @Test public void fractionalOrWrongTypedAmountsAndIdsNeverReachDatabase()throws Exception{
        for(String amount:Arrays.asList("1.5","\"5\"","true","null","0","-1","10000000"))
            http.perform(post("/api/v1/recharge").contentType("application/json").content(body("22",amount,"credit"))).andExpect(status().isBadRequest());
        for(String uid:Arrays.asList("22.5","true","\"22 OR 1=1\"","0","2147483648"))
            http.perform(post("/api/v1/recharge").contentType("application/json").content(body(uid,"5","credit"))).andExpect(status().isBadRequest());
        verify(journal,never()).connection();
    }
    @Test public void mismatchedTargetAndUnlinkedCompensationAreRejected()throws Exception{
        http.perform(post("/api/v1/recharge/23/preview").contentType("application/json").content(body("22","5","credit"))).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/recharge").contentType("application/json").content(body("22","-5","compensate"))).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/recharge").contentType("application/json").content(body("22","5","restore"))).andExpect(status().isBadRequest());verify(journal,never()).connection();
    }
    @Test public void nonAdministratorCannotUseAnyReadOrWriteRoute()throws Exception{
        when(journal.admin()).thenThrow(new DofFailure(403,"管理员权限不足"));
        http.perform(get("/api/v1/recharge/22")).andExpect(status().isForbidden());
        http.perform(get("/api/v1/recharge/22/history")).andExpect(status().isForbidden());
        http.perform(post("/api/v1/recharge/22/history/00000000-0000-4000-8000-000000000001/reconcile")).andExpect(status().isForbidden());
        http.perform(post("/api/v1/recharge").contentType("application/json").content(body("22","5","credit"))).andExpect(status().isForbidden());verify(journal,never()).connection();
    }
}
