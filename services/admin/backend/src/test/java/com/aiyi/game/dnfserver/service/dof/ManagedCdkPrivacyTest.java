package com.aiyi.game.dnfserver.service.dof;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManagedCdkPrivacyTest {
    @Test public void requestSecretsBindButAreNotEchoedOrSerializedByTheCoreAutoLogger()throws Exception{
        ObjectMapper json=new ObjectMapper();ManagedCdkService.Claim e=json.readValue("{\"content\":\"synthetic-private-code\",\"valicode\":\"SYNTH\",\"valiId\":\"synthetic-challenge-id\",\"useAccount\":\"player\",\"charach\":123,\"face\":9999}",ManagedCdkService.Claim.class);
        assertEquals("synthetic-private-code",e.content);assertEquals(123,e.charach.intValue());
        for(String value:new String[]{json.writeValueAsString(e),com.alibaba.fastjson.JSON.toJSONString(e)}){assertFalse(value.contains("synthetic-private-code"));assertFalse(value.contains("SYNTH"));assertFalse(value.contains("synthetic-challenge-id"));}
        RechargeKey legacy=new RechargeKey();legacy.setContent("synthetic-private-code");assertFalse(com.alibaba.fastjson.JSON.toJSONString(legacy).contains("synthetic-private-code"));assertTrue(json.writeValueAsString(legacy).contains("synthetic-private-code")); // Authorized legacy list still uses Jackson.
    }
}
