package com.aiyi.game.dnfserver.service.dof;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeBasicColorsTest {
    private final ObjectMapper json=new ObjectMapper();
    private final String source="010203***aAbBcC***000000***FFFFFF***4294967295***原样保留***1***123456***789ABC***AA0000***00BB00";
    private Map<String,JsonNode> change(String key,String value)throws Exception {Map<String,JsonNode> m=new LinkedHashMap<>();m.put(key,json.readTree(value));return m;}
    @Test public void eachColorTouchesOnlyItsNativePosition()throws Exception {
        String[] keys={"npcName","characterName","otherName","bubblePrimary","channel","hintPrimary","bubbleSecondary","hintSecondary"};
        int[] indices={0,1,2,3,7,8,9,10};
        for(int i=0;i<keys.length;i++) {
            String[] before=source.split("\\*\\*\\*",-1),after=NativeBasicColors.edit(source,change(keys[i],"\"#654321\"")).split("\\*\\*\\*",-1);
            for(int j=0;j<11;j++)assertEquals(j==indices[i]?"654321":before[j],after[j]);
        }
        assertEquals(9,NativeBasicColors.inspect(source).size());
    }
    @Test public void mixedCaseAndReservedValuesSurviveSemanticNoop()throws Exception {
        assertEquals(source,NativeBasicColors.edit(source,change("characterName","\"#aabbcc\"")));
        assertEquals(source,NativeBasicColors.edit(source,change("customNames","true")));
        String changed=NativeBasicColors.edit(source,change("customNames","false"));
        assertEquals(source.replace("***1***","***0***"),changed);
    }
    @Test public void malformedValuesAndUnrecognizedLayoutsFailClosed()throws Exception {
        for(String value:Arrays.asList("0","true","null","\"#12345678\"","\"123456\"","\"#ZZZZZZ\"")) {
            try{NativeBasicColors.edit(source,change("npcName",value));fail();}catch(DofFailure e){assertEquals(400,e.status);}
        }
        for(String raw:Arrays.asList("",source+"***future",source.substring(0,source.lastIndexOf("***")))) {
            try{NativeBasicColors.inspect(raw);fail();}catch(DofFailure e){assertEquals(409,e.status);}
        }
        try{NativeBasicColors.edit(source,change("reserved-4","\"#123456\""));fail();}catch(DofFailure e){assertEquals(400,e.status);}
    }
    @Test public void unknownExistingColorIsPreservedWhenAnotherFieldChanges()throws Exception {
        String unknown=source.replace("010203","native-extension");
        assertEquals(false,NativeBasicColors.inspect(unknown).get(0).get("available"));
        assertTrue(NativeBasicColors.edit(unknown,change("channel","\"#ABCDEF\"")).startsWith("native-extension***"));
        try{NativeBasicColors.edit(unknown,change("npcName","\"#123456\""));fail();}catch(DofFailure e){assertEquals(409,e.status);}
    }
}
