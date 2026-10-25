package com.aiyi.game.dnfserver.service.dof;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class NativeGeneralSettingsTest {
    private final ObjectMapper json=new ObjectMapper();
    static Map<String,String> fixture() {
        Map<String,String> out=new LinkedHashMap<>();for(String key:NativeGeneralSettings.COLUMNS)out.put(key,b64("1"));
        out.put("config.Switch",b64(String.join("*",Collections.nCopies(40,"真"))+"*future-value**"));return out;
    }
    static String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private Map<String,JsonNode> change(String key,String value)throws Exception {Map<String,JsonNode> out=new LinkedHashMap<>();out.put(key,json.readTree(value));return out;}
    @Test public void allShortcutPositionsPreserveTrailingAndUnknownFields()throws Exception {
        for(int i=0;i<40;i++) {
            Map<String,String> source=fixture(),target=NativeGeneralSettings.apply("shortcuts",source,change("shortcut-"+i,"false"));
            String[] parts=NativeGeneralSettings.raw(target,"config.Switch").split("\\*",-1);
            assertEquals(43,parts.length);assertEquals("future-value",parts[40]);assertEquals("",parts[41]);assertEquals("",parts[42]);
            for(int j=0;j<40;j++)assertEquals(j==i?"假":"真",parts[j]);
            for(String key:source.keySet())if(!key.endsWith(".Switch"))assertEquals(source.get(key),target.get(key));
        }
    }
    @Test public void semanticNoopPreservesZeroSpellingAndUnknownBytes()throws Exception {
        Map<String,String> source=fixture();source.put("config.Switch",b64("0*"+String.join("*",Collections.nCopies(39,"真"))+"*未知*"));
        assertEquals(source,NativeGeneralSettings.apply("shortcuts",source,change("shortcut-0","false")));
        assertEquals(source,NativeGeneralSettings.apply("registration",source,change("ipLimit","1")));
    }
    @Test public void incompatibleInputsCannotBecomeNativeValues()throws Exception {
        for(String value:Arrays.asList("null","\"1\"","1.5","2147483648","-1","true")) {
            try{NativeGeneralSettings.apply("registration",fixture(),change("registrationGift",value));fail();}catch(DofFailure e){assertEquals(400,e.status);}
        }
        try{NativeGeneralSettings.apply("registration",fixture(),change("shortcut-0","false"));fail();}catch(DofFailure e){assertEquals(400,e.status);}
        Map<String,String> missing=fixture();missing.put("config.Switch",b64("真*"));
        try{NativeGeneralSettings.apply("shortcuts",missing,change("shortcut-0","false"));fail();}catch(DofFailure e){assertEquals(409,e.status);}
    }
    @Test public void bothNativeCurrencyOptionsAndBooleanTypesAreExplicit()throws Exception {
        for(int v:Arrays.asList(0,1))assertEquals(""+v,NativeGeneralSettings.raw(NativeGeneralSettings.apply("registration",fixture(),change("registrationCurrency",""+v)),"billing.regtype"));
        try{NativeGeneralSettings.apply("registration",fixture(),change("registrationCurrency","3"));fail();}catch(DofFailure e){assertEquals(400,e.status);}
        try{NativeGeneralSettings.apply("registration",fixture(),change("passwordChange","0"));fail();}catch(DofFailure e){assertEquals(400,e.status);}
        assertEquals(5,NativeGeneralSettings.inspect("registration",fixture()).size());assertEquals(3,NativeGeneralSettings.inspect("online-reward",fixture()).size());assertEquals(40,NativeGeneralSettings.inspect("shortcuts",fixture()).size());
    }
    @Test public void nativeNegativeIpLimitAndNonpositiveDelayRemainRepresentable()throws Exception {
        for(int value:new int[]{Integer.MIN_VALUE,-1,0,60}) {
            assertEquals(""+value,NativeGeneralSettings.raw(NativeGeneralSettings.apply("registration",fixture(),change("ipLimit",""+value)),"billing.ip_reg_control"));
            assertEquals(""+value,NativeGeneralSettings.raw(NativeGeneralSettings.apply("online-reward",fixture(),change("rewardInterval",""+value)),"billing.bubbleTime"));
        }
    }
}
