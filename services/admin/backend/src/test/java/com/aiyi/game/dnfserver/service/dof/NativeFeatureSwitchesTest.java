package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeFeatureSwitchesTest {
    private NativeUiState sample() throws Exception {
        ObjectMapper json=new ObjectMapper();
        return NativeUiState.fromText(json.convertValue(json.readTree(PrivateNativeFixtures.open("ui-native-100.json")).get("after"),new TypeReference<Map<String,String>>(){}));
    }
    private NativeUiState keys(NativeUiState state,String encoded) {
        Map<String,byte[]> copy=new LinkedHashMap<>();for(String key:NativeUiState.COLUMNS)copy.put(key,state.get(key));
        copy.put("Hotkeys",encoded.getBytes(StandardCharsets.US_ASCII));return new NativeUiState(copy);
    }
    @Test public void nativeCheckboxCatalogDoesNotDuplicateUiControlsOrInventLegacyDefaults() throws Exception {
        NativeUiState state=sample();List<Map<String,Object>> fields=NativeFeatureSwitches.inspect(state);assertEquals(70,fields.size());
        Set<Integer> existing=new HashSet<>(Arrays.asList(20,26,51,55,56,60,69,70,71,74,89,99,101,102,125,126));
        for(Map<String,Object> field:fields)assertFalse(existing.contains(Integer.valueOf(field.get("key").toString().substring(7))));
        NativeHotkeys before=NativeHotkeys.decode(state.text("Hotkeys"));
        state=keys(state,before.withText(Collections.singletonMap(136,"unrecognized-native-value")).encode());
        assertEquals(false,NativeFeatureSwitches.inspect(state).get(69).get("available"));
        try {NativeFeatureSwitches.apply(state,Collections.singletonMap("native-136",true));fail();}
        catch(DofFailure expected) {assertEquals(409,expected.status);}
        ObjectMapper json=new ObjectMapper();
        NativeUiState legacy=NativeUiState.fromText(json.convertValue(json.readTree(PrivateNativeFixtures.open("ui-native-100.json")).get("before"),new TypeReference<Map<String,String>>(){}));
        assertEquals(100,NativeHotkeys.decode(legacy.text("Hotkeys")).size());
        try {NativeFeatureSwitches.apply(legacy,Collections.singletonMap("native-136",true));fail();}
        catch(DofFailure expected) {assertEquals(409,expected.status);}
    }
    @Test public void oneSwitchPreservesEveryOtherRawValueAndNoopPreservesLegacyZero() throws Exception {
        NativeUiState before=sample();NativeHotkeys original=NativeHotkeys.decode(before.text("Hotkeys"));
        before=keys(before,original.withText(Collections.singletonMap(136,"0")).encode());
        original=NativeHotkeys.decode(before.text("Hotkeys"));
        assertEquals(before.revision(),NativeFeatureSwitches.apply(before,Collections.singletonMap("native-136",false)).revision());
        NativeUiState after=NativeFeatureSwitches.apply(before,Collections.singletonMap("native-136",true));
        NativeHotkeys result=NativeHotkeys.decode(after.text("Hotkeys"));assertEquals("真",result.text(136));
        for(int i=0;i<original.size();i++)if(i!=136)assertArrayEquals(original.raw(i),result.raw(i));
        assertArrayEquals(before.get("temp_2"),after.get("temp_2"));assertArrayEquals(before.get("temp_3"),after.get("temp_3"));
    }
    @Test public void invalidOrMixedUnknownPatchCannotPartiallyModifyConfiguration() throws Exception {
        NativeUiState before=sample();String version=before.revision();
        for(Map<String,Boolean> patch:Arrays.asList(Collections.singletonMap("native-999",true),Collections.singletonMap("native-136",(Boolean)null),Collections.<String,Boolean>emptyMap())) {
            try {NativeFeatureSwitches.apply(before,patch);fail();}catch(DofFailure expected) {assertEquals(400,expected.status);}
        }
        Map<String,Boolean> patch=new LinkedHashMap<>();patch.put("native-136",true);patch.put("unexpected",true);
        try {NativeFeatureSwitches.apply(before,patch);fail();}catch(DofFailure expected) {assertEquals(400,expected.status);}
        assertEquals(version,before.revision());
    }
    @Test public void earringRemainsVisibleButUnknownNativeSaveIsBlocked() throws Exception {
        NativeUiState state=sample();Map<String,Object> field=NativeFeatureSwitches.inspect(state).stream().filter(f->"native-130".equals(f.get("key"))).findFirst().get();
        assertEquals(false,field.get("editable"));assertNotNull(field.get("blockedReason"));
        try {NativeFeatureSwitches.apply(state,Collections.singletonMap("native-130",true));fail();}
        catch(DofFailure expected) {assertEquals(409,expected.status);}
    }
}
