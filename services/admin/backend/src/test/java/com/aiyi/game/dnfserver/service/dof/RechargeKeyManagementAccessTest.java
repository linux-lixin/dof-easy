package com.aiyi.game.dnfserver.service.dof;
import com.aiyi.game.dnfserver.dao.RechargeKeyDao;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.impl.RechargeKeyServiceImpl;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RechargeKeyManagementAccessTest {
    @Test public void ordinaryLoginCannotReadSecretsOrMintCodesThroughTheLegacyService(){
        ManagedCdkService access=mock(ManagedCdkService.class);doThrow(new DofFailure(403,"not administrator")).when(access).requireAdmin();RechargeKeyDao dao=mock(RechargeKeyDao.class);RechargeKeyServiceImpl service=new RechargeKeyServiceImpl();ReflectionTestUtils.setField(service,"managedCdk",access);ReflectionTestUtils.setField(service,"rechargeKeyDao",dao);
        for(Runnable action:new Runnable[]{()->service.list(null,null,1,10,null),()->service.create(new RechargeKey())}){try{action.run();fail("Ordinary login accessed CDK management");}catch(DofFailure e){assertEquals(403,e.status);}}
        verifyZeroInteractions(dao);
    }
}
