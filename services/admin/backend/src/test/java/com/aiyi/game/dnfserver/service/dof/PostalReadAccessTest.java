package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.controller.PostalController;
import com.aiyi.game.dnfserver.dao.*;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.service.impl.PostalServiceImpl;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PostalReadAccessTest {
    private PostalQueryDao query;private AccountVODao accounts;private PostalServiceImpl service;private MockMvc http;
    @Before public void setup() {
        ThreadUtil.setUserId(7L);query=mock(PostalQueryDao.class);accounts=mock(AccountVODao.class);service=new PostalServiceImpl();
        ReflectionTestUtils.setField(service,"accountVODao",accounts);ReflectionTestUtils.setField(service,"postalQueryDao",query);
        AccountVO actor=new AccountVO();actor.setUid(7L);actor.setAdmin(false);when(accounts.get(7L)).thenReturn(actor);
        when(query.list(any(),anyBoolean(),anyLong(),any(),any(),anyInt(),anyInt())).thenReturn(Collections.emptyList());
        PostalController controller=new PostalController();ReflectionTestUtils.setField(controller,"postalService",service);
        http=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @After public void clear(){ThreadUtil.setUserId(1L);}
    private void reject(int status,Runnable action){try{action.run();fail("request accepted");}catch(DofFailure e){assertEquals(status,e.status);}}
    @Test public void missingActorRejectedBeforeReadingMail() {
        ThreadUtil.setUserId(0L);reject(403,()->service.list(null,null,null,1,10));verifyZeroInteractions(query,accounts);
        ThreadUtil.setUserId(7L);when(accounts.get(7L)).thenReturn(null);reject(403,()->service.list(null,null,null,1,10));verifyZeroInteractions(query);
    }
    @Test public void invalidPaginationAndReversedPeriodDoNotQueryMail() {
        for(int[] v:new int[][]{{0,10},{-1,10},{1,0},{1,101},{Integer.MAX_VALUE,100}})
            reject(400,()->service.list(null,null,null,v[0],v[1]));
        reject(400,()->service.list(null,new Date(2),new Date(1),1,10));verifyZeroInteractions(query);
    }
    @Test public void paginationAndUnknownAccountRemainScoped() {
        service.list("missing",null,null,3,20);
        verify(query).count("missing",false,7L,null,null);verify(query).list("missing",false,7L,null,null,40,20);
    }
    @Test public void invalidDatesAreHttp400InsteadOfSilentlyUnfiltered()throws Exception {
        for(String date:Arrays.asList("bad","2026-02-30","2026-01-01extra","2026-01-01 24:00:00"))
            http.perform(get("/api/v1/postal").param("start",date)).andExpect(status().isBadRequest());
        verifyZeroInteractions(query);
    }
    @Test public void endDateIncludesWholeDayAndResponseKeepsRequestedPage()throws Exception {
        http.perform(get("/api/v1/postal").param("account","absent").param("start","2026-01-02").param("end","2026-01-02").param("page","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalSize").value(0));
        ArgumentCaptor<Date> start=ArgumentCaptor.forClass(Date.class),end=ArgumentCaptor.forClass(Date.class);
        verify(query).count(eq("absent"),eq(false),eq(7L),start.capture(),end.capture());
        Calendar a=Calendar.getInstance();a.setTime(start.getValue());assertEquals(0,a.get(Calendar.HOUR_OF_DAY));
        a.setTime(end.getValue());assertEquals(23,a.get(Calendar.HOUR_OF_DAY));assertEquals(59,a.get(Calendar.MINUTE));assertEquals(59,a.get(Calendar.SECOND));assertEquals(999,a.get(Calendar.MILLISECOND));
    }
    @Test public void queryFailureIsAnErrorResponseNotAnEmptyPage()throws Exception {
        when(query.count(any(),anyBoolean(),anyLong(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database details"));
        http.perform(get("/api/v1/postal")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.list").doesNotExist());
        verify(query,never()).list(any(),anyBoolean(),anyLong(),any(),any(),anyInt(),anyInt());
    }
    @Test public void legacySendFailureDoesNotClaimThatNoWriteOccurred()throws Exception {
        PostalDao writes=mock(PostalDao.class);ReflectionTestUtils.setField(service,"postalDao",writes);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic lost write reply")).when(writes).add(any(com.aiyi.game.dnfserver.entity.Postal.class));
        http.perform(post("/api/v1/postal").contentType("application/json").content("{\"receiveCharacNo\":\"101\",\"itemId\":5001}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message",org.hamcrest.Matchers.containsString("结果尚未确认")))
            .andExpect(jsonPath("$.message",org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("没有修改数据"))));
    }
}
