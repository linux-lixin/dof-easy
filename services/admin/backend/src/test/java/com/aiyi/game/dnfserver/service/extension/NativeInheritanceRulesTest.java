package com.aiyi.game.dnfserver.service.extension;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeInheritanceRulesTest {
    private final ActivityConfigService service = new ActivityConfigService();
    private ActivityConfigService.Section section() {return serviceSection();}
    private ActivityConfigService.Section serviceSection() {
        // The catalog's authorization is tested separately; load the same source descriptor.
        try (java.io.InputStream in=getClass().getResourceAsStream("/extension/activities.json")) {
            ActivityConfigService.Section[] all=new com.fasterxml.jackson.databind.ObjectMapper().readValue(in,ActivityConfigService.Section[].class);
            return Arrays.stream(all).filter(s->s.key.equals("inheritance")).findFirst().get();
        }catch(Exception e){throw new AssertionError(e);}
    }
    private Map<String,Object> rule(String items,String counts) {
        Map<String,Object> v=new LinkedHashMap<>();v.put("code",0);v.put("restitution",items);v.put("Return_quantity",counts);v.put("differ_lv",5);return v;
    }
    private void rejected(Runnable action) {try{action.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(400,e.status);}}
    @Test public void zeroDefaultAndFiveMaterialPairsKeepTheirExactWireText() {
        Map<String,Object> value=rule("0003037/3340/1/2/3","10/20/30/40/50");
        Map<String,Object> actual=service.validate(section(),value,null,true);
        assertEquals(0L,actual.get("code"));assertEquals(value.get("restitution"),actual.get("restitution"));
        assertTrue(section().writable);assertTrue(section().deleteAllowed);
    }
    @Test public void emptyCostRequiresBothEmptyAndNoMalformedOrMismatchedGroups() {
        assertEquals("",service.validate(section(),rule("",""),null,true).get("restitution"));
        for(String[] pair:new String[][]{{"1",""},{"","1"},{"1/2","1"},{"1//2","1/2/3"},{"1/","1/2"},{"1 2","1"},{"1.0","1"},{"-1","1"},{"0","1"},{"1","0"},{"2147483648","1"},{"1/2/3/4/5/6","1/2/3/4/5/6"}})
            rejected(()->service.validate(section(),rule(pair[0],pair[1]),null,true));
    }
    @Test public void existingOpaqueCostIsNotRewrittenByLevelDifferenceEdit() {
        Map<String,Object> before=rule("old native extension","legacy-counts"),next=new LinkedHashMap<>(before);
        next.put("differ_lv",10);
        assertEquals(before.get("restitution"),service.validate(section(),next,before,false).get("restitution"));
        next.put("restitution","3037");rejected(()->service.validate(section(),next,before,false));
    }
    @Test public void restorationTreatsCostsAsOnePairAndKeepsUnrelatedLevelChanges() {
        Map<String,Object> before=rule("3037","1"),after=rule("3340","1"),current=new LinkedHashMap<>(after);
        current.put("differ_lv",20);NativeInheritanceRules.restoreGuard(before,after,current);
        current.put("Return_quantity","2");
        try{NativeInheritanceRules.restoreGuard(before,after,current);fail();}
        catch(ActivityConfigService.Failure e){assertEquals(409,e.status);}
    }
}
