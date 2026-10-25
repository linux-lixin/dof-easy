package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeFeatureValuesTest {
    private NativeUiState sample(String part) throws Exception {
        ObjectMapper json=new ObjectMapper();
        return NativeUiState.fromText(json.convertValue(json.readTree(PrivateNativeFixtures.open("ui-native-100.json")).get(part),new TypeReference<Map<String,String>>(){}));
    }
    @Test public void allTwentyTwoFieldsPreserveExactTextAndUnrelatedBytes() throws Exception {
        NativeUiState state=sample("after");NativeHotkeys initial=NativeHotkeys.decode(state.text("Hotkeys"));
        List<Map<String,Object>> fields=NativeFeatureValues.inspect(state);assertEquals(87,fields.size());
        fields.removeIf(f->"耳环与他人面板".equals(f.get("group")));assertEquals(22,fields.size());
        Set<Integer> indices=new HashSet<>(Arrays.asList(31,33,34,35,36,37,44,49,58,105,109,110,111,112,113,114,115,116,117,118,119,121));
        Map<String,String> patch=new LinkedHashMap<>();
        for(Map<String,Object> field:fields) {assertEquals(true,field.get("available"));patch.put(field.get("key").toString(),"  配置007  ");}
        NativeUiState changed=NativeFeatureValues.apply(state,patch);
        NativeHotkeys result=NativeHotkeys.decode(changed.text("Hotkeys"));
        for(int i=0;i<initial.size();i++) {
            if(indices.contains(i))assertEquals("  配置007  ",result.text(i));else assertArrayEquals(initial.raw(i),result.raw(i));
        }
        assertArrayEquals(state.get("temp_2"),changed.get("temp_2"));assertArrayEquals(state.get("temp_3"),changed.get("temp_3"));
        assertSame(changed,NativeFeatureValues.apply(changed,patch));
        NativeUiState empty=NativeFeatureValues.apply(changed,Collections.singletonMap("dungeonList",""));
        assertEquals("",NativeHotkeys.decode(empty.text("Hotkeys")).text(121));
    }
    @Test public void malformedInputCannotPartiallyModifyOrCorruptNativeDelimiter() throws Exception {
        NativeUiState state=sample("after");String version=state.revision();
        for(String value:Arrays.asList(null,"abc*def","a\nb","a\u0000b","\ud83d\ude00",String.join("",Collections.nCopies(65536,"a")))) {
            try {NativeFeatureValues.apply(state,Collections.singletonMap("contractLevel",value));fail();}
            catch(DofFailure e){assertEquals(400,e.status);}
        }
        Map<String,String> mixed=new LinkedHashMap<>();mixed.put("contractLevel","70");mixed.put("unknown","80");
        try {NativeFeatureValues.apply(state,mixed);fail();}catch(DofFailure e){assertEquals(400,e.status);}
        try {NativeFeatureValues.apply(state,Collections.emptyMap());fail();}catch(DofFailure e){assertEquals(400,e.status);}
        assertEquals(version,state.revision());
    }
    @Test public void oldConfigurationShowsMissingFieldsWithoutFabricatingValues() throws Exception {
        NativeUiState state=sample("before");
        assertEquals(false,NativeFeatureValues.inspect(state).stream().filter(f->"dungeonList".equals(f.get("key"))).findFirst().get().get("available"));
        try {NativeFeatureValues.apply(state,Collections.singletonMap("dungeonList","12,34"));fail();}
        catch(DofFailure e){assertEquals(409,e.status);}
    }

    @Test public void compositeEditsPreserveOtherPartsReservedSlotsAndFutureExtensions() throws Exception {
        NativeUiState state=sample("after");NativeHotkeys original=NativeHotkeys.decode(state.text("Hotkeys"));
        String[] parts=new String[70];for(int i=0;i<parts.length;i++)parts[i]="原值"+i;
        Map<String,String> raw=new LinkedHashMap<>();for(String column:NativeUiState.COLUMNS)raw.put(column,state.text(column));
        raw.put("Hotkeys",original.withText(Collections.singletonMap(131,String.join("##",parts))).encode());
        state=NativeUiState.fromText(raw);original=NativeHotkeys.decode(state.text("Hotkeys"));
        Map<String,String> patch=new LinkedHashMap<>();patch.put("earring.typeName"," 耳环 ");patch.put("earring.attackMultiplier1","1.5");patch.put("earring.position66","-20");
        NativeUiState changed=NativeFeatureValues.apply(state,patch);NativeHotkeys keys=NativeHotkeys.decode(changed.text("Hotkeys"));
        String[] actual=keys.text(131).split("##",-1);assertEquals(70,actual.length);
        for(int i=0;i<70;i++)assertEquals(i==0?" 耳环 ":i==2?"1.5":i==66?"-20":parts[i],actual[i]);
        for(int i=0;i<original.size();i++)if(i!=131)assertArrayEquals(original.raw(i),keys.raw(i));
        assertArrayEquals(state.get("temp_2"),changed.get("temp_2"));assertArrayEquals(state.get("temp_3"),changed.get("temp_3"));
        assertSame(changed,NativeFeatureValues.apply(changed,patch));
        for(String invalid:Arrays.asList("left##right","suffix#")) {
            try {NativeFeatureValues.apply(state,Collections.singletonMap("earring.typeName",invalid));fail();}
            catch(DofFailure e){assertEquals(400,e.status);}
        }
    }
    @Test public void missingCompositeIsVisibleAndNeverInitializedByEditing() throws Exception {
        NativeUiState state=sample("before");
        assertEquals(65,NativeFeatureValues.inspect(state).stream().filter(f->"耳环与他人面板".equals(f.get("group")) && Boolean.FALSE.equals(f.get("available"))).count());
        try {NativeFeatureValues.apply(state,Collections.singletonMap("earring.typeName","耳环"));fail();}
        catch(DofFailure e){assertEquals(409,e.status);}
    }
}
