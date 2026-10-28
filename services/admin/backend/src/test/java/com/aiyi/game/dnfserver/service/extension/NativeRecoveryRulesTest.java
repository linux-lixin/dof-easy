package com.aiyi.game.dnfserver.service.extension;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecoveryRulesTest {
    private final ActivityConfigService service=new ActivityConfigService();
    private ActivityConfigService.Section section(){return ((java.util.List<ActivityConfigService.Section>)org.springframework.test.util.ReflectionTestUtils.getField(service,"sections")).stream().filter(s->s.key.equals("recovery")).findFirst().get();}
    private Map<String,Object> rule(){Map<String,Object> v=new LinkedHashMap<>();v.put("code",27600);v.put("restitution","3037/3340");v.put("Return_quantity","1/2");v.put("announcement",0);v.put("codenum",1);v.put("extra_consumption_id","0/3037");v.put("extra_consumption_num","500/1");return v;}
    private void reject(Runnable r){try{r.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(400,e.status);}}
    @Test public void sevenPairsAndGoldZeroKeepExactWireText() {
        Map<String,Object> v=rule();v.put("restitution","1/2/3/4/5/6/7");v.put("Return_quantity","01/02/03/04/05/06/07");
        assertEquals(v.get("Return_quantity"),service.validate(section(),v,null,true).get("Return_quantity"));
        assertEquals("0/3037",service.validate(section(),v,null,true).get("extra_consumption_id"));
        v.put("extra_consumption_id","");v.put("extra_consumption_num","");service.validate(section(),v,null,true);
    }
    @Test public void malformedOrMispairedRewardsAndExtraCostsAreRefused() {
        for(String key:Arrays.asList("restitution","Return_quantity","extra_consumption_id","extra_consumption_num")) {
            for(String bad:Arrays.asList("1/","/1","1//2","1.2","-1"," 1","2147483648","1/2/3/4/5/6/7/8")) {
                Map<String,Object> v=rule();v.put(key,bad);reject(()->service.validate(section(),v,null,true));
            }
        }
        for(String key:Arrays.asList("restitution","Return_quantity","extra_consumption_num")) {Map<String,Object> v=rule();v.put(key,"0/1");reject(()->service.validate(section(),v,null,true));}
        for(String key:Arrays.asList("code","codenum")) {Map<String,Object> v=rule();v.put(key,0);reject(()->service.validate(section(),v,null,true));}
        Map<String,Object> v=rule();v.put("announcement",2);reject(()->service.validate(section(),v,null,true));
    }
    @Test public void opaqueLegacyPairsAndFlagsSurviveUnrelatedChanges() {
        Map<String,Object> old=rule();old.put("restitution","legacy reward");old.put("Return_quantity","opaque");old.put("extra_consumption_id","legacy debit");old.put("extra_consumption_num","opaque");old.put("codenum",0);old.put("announcement",9);
        Map<String,Object> next=new LinkedHashMap<>(old);next.put("announcement",1);
        assertEquals("legacy reward",service.validate(section(),next,old,false).get("restitution"));
        next.put("extra_consumption_num","1");reject(()->service.validate(section(),next,old,false));
    }
    @Test public void eachRestorationPairDetectsForeignChangesWithoutBlockingIndependentFields() {
        for(String[] pair:new String[][]{{"restitution","Return_quantity"},{"extra_consumption_id","extra_consumption_num"}}) {
            Map<String,Object> before=rule(),after=new LinkedHashMap<>(before);after.put(pair[0],"3340");Map<String,Object> current=new LinkedHashMap<>(after);current.put("announcement",1);NativeRecoveryRules.restoreGuard(before,after,current);
            current.put(pair[1],"foreign");try{NativeRecoveryRules.restoreGuard(before,after,current);fail();}catch(ActivityConfigService.Failure e){assertEquals(409,e.status);}
        }
    }
}
