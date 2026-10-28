package com.aiyi.game.dnfserver.service.extension;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeCrossBorderRulesTest {
    private final ActivityConfigService service=new ActivityConfigService();
    private ActivityConfigService.Section section(){try(java.io.InputStream in=getClass().getResourceAsStream("/extension/activities.json")){return Arrays.stream(new com.fasterxml.jackson.databind.ObjectMapper().readValue(in,ActivityConfigService.Section[].class)).filter(s->s.key.equals("cross-border")).findFirst().get();}catch(Exception e){throw new AssertionError(e);}}
    private Map<String,Object> rule(){Map<String,Object> v=new LinkedHashMap<>();v.put("code",0);v.put("restitution","3037/3340");v.put("Return_quantity","10/20");v.put("announcement",1);v.put("codenum",1);return v;}
    private void reject(int status,Runnable r){try{r.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(status,e.status);}}
    @Test public void zeroFallbackFivePairsAndIndependentThresholdPreserveWireText(){
        Map<String,Object> v=rule();v.put("restitution","01/2/3/4/5");v.put("Return_quantity","10/20/30/40/50");v.put("codenum",0);Map<String,Object> actual=service.validate(section(),v,null,true);assertEquals(0L,actual.get("code"));assertEquals(0L,actual.get("codenum"));assertEquals(v.get("Return_quantity"),actual.get("Return_quantity"));assertTrue(section().writable);
    }
    @Test public void malformedPairsAndUnsupportedNewReservedValuesNeverPass(){
        for(String key:Arrays.asList("restitution","Return_quantity"))for(String bad:Arrays.asList("1/","/1","1//2","0/1","1/2/3/4/5/6","1.0","-1","2147483648"," 1")){Map<String,Object> v=rule();v.put(key,bad);reject(400,()->service.validate(section(),v,null,true));}
        Map<String,Object> v=rule();v.put("announcement",0);reject(400,()->service.validate(section(),v,null,true));
    }
    @Test public void opaqueLegacyMaterialsAndReservedFlagSurviveThresholdEdits(){
        Map<String,Object> old=rule();old.put("restitution","opaque");old.put("Return_quantity","old amounts");old.put("announcement",4294967295L);Map<String,Object> next=new LinkedHashMap<>(old);next.put("codenum",5);Map<String,Object> actual=service.validate(section(),next,old,false);assertEquals(4294967295L,actual.get("announcement"));assertEquals("opaque",actual.get("restitution"));next.put("announcement",1);reject(400,()->service.validate(section(),next,old,false));
    }
    @Test public void restorationTreatsThresholdAndBothMaterialColumnsAsOneGroup(){
        for(String changed:Arrays.asList("restitution","Return_quantity","codenum")){
            Map<String,Object> before=rule(),after=new LinkedHashMap<>(before);after.put(changed,changed.equals("codenum")?2L:"5/6");Map<String,Object> current=new LinkedHashMap<>(after);current.put("announcement",9);NativeCrossBorderRules.restoreGuard(before,after,current);
            String other=changed.equals("codenum")?"Return_quantity":"codenum";current.put(other,other.equals("codenum")?3L:"9/9");reject(409,()->NativeCrossBorderRules.restoreGuard(before,after,current));
        }
    }
}
