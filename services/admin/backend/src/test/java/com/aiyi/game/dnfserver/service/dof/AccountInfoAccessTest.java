package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.service.impl.AccountServiceImpl;
import com.aiyi.game.dnfserver.controller.AccountController;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AccountInfoAccessTest {
    private AccountVODao dao;private AccountServiceImpl service;private AccountVO actor,target,balance;
    private static final String BILLING="SELECT cera FROM taiwan_billing.cash_cera WHERE account=?";
    @Before public void setup() {
        ThreadUtil.setUserId(1L);dao=mock(AccountVODao.class);service=new AccountServiceImpl();ReflectionTestUtils.setField(service,"accountVODao",dao);
        actor=new AccountVO();actor.setUid(1L);actor.setAdmin(true);
        target=new AccountVO();target.setUid(22L);target.setParentUid(1L);target.setPassword("synthetic-stored-hash");target.setAccountname("test-target");
        balance=new AccountVO();balance.setCera(37);
        when(dao.get(1L)).thenReturn(actor);when(dao.get(22L)).thenReturn(target);
        when(dao.list(BILLING,22L)).thenReturn(Collections.singletonList(balance));
    }
    @After public void resetActor(){ThreadUtil.setUserId(1L);}
    private void reject(int status,Runnable operation){try{operation.run();fail("request accepted");}catch(DofFailure e){assertEquals(status,e.status);}}
    @Test public void administratorGetsSafeCopyWithoutMutatingStoredAccount() {
        AccountVO response=service.info(22L);assertEquals(22L,response.getUid());assertEquals(37,response.getCera());assertNull(response.getPassword());
        assertNotSame(target,response);assertEquals("synthetic-stored-hash",target.getPassword());
    }
    @Test public void guildOwnerCanReadOwnChildWithoutPassword() {
        actor.setAdmin(false);assertNull(service.info(22L).getPassword());
    }
    @Test public void selfReadRemainsAllowedWithoutChangingCachedActor() {
        actor.setAdmin(false);actor.setPassword("synthetic-actor-hash");when(dao.list(BILLING,1L)).thenReturn(Collections.singletonList(balance));
        assertNull(service.info(1L).getPassword());assertEquals("synthetic-actor-hash",actor.getPassword());
    }
    @Test public void foreignGuildRejectedBeforeBillingRead() {
        actor.setAdmin(false);target.setParentUid(9L);reject(403,()->service.info(22L));
        verify(dao).get(1L);verify(dao).get(22L);verifyNoMoreInteractions(dao);
    }
    @Test public void invalidActorCannotReadTarget() {
        ThreadUtil.setUserId(0L);reject(403,()->service.info(22L));verifyZeroInteractions(dao);
        ThreadUtil.setUserId(1L);when(dao.get(1L)).thenReturn(null);reject(403,()->service.info(22L));verify(dao,never()).get(22L);
    }
    @Test public void missingTargetAndAmbiguousBillingHaveSpecificFailures() {
        when(dao.get(22L)).thenReturn(null);reject(404,()->service.info(22L));
        when(dao.get(22L)).thenReturn(target);when(dao.list(BILLING,22L)).thenReturn(Collections.emptyList());reject(409,()->service.info(22L));
        when(dao.list(BILLING,22L)).thenReturn(Arrays.asList(balance,balance));reject(409,()->service.info(22L));
    }
    @Test public void actualHttpSerializationOmitsHashAndReportsPermission() throws Exception {
        AccountController controller=new AccountController();ReflectionTestUtils.setField(controller,"accountService",service);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/account/22")).andExpect(status().isOk()).andExpect(jsonPath("$.cera").value(37)).andExpect(jsonPath("$.password").doesNotExist());
        actor.setAdmin(false);target.setParentUid(9L);
        mvc.perform(get("/api/v1/account/22")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").isNotEmpty());
    }
}
