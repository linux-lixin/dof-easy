package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeUiSwitchesTest {
    private NativeUiState sample() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Map<String,Object> root = json.readValue(PrivateNativeFixtures.open("ui-native-100.json"),new TypeReference<Map<String,Object>>(){});
        return NativeUiState.fromText(json.convertValue(root.get("after"),new TypeReference<Map<String,String>>(){}));
    }
    private NativeUiState replace(NativeUiState state,String key,String value) {
        Map<String,byte[]> fields = new LinkedHashMap<>();
        for (String k : NativeUiState.COLUMNS) fields.put(k,state.get(k));
        fields.put(key,value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return new NativeUiState(fields);
    }
    @Test public void togglesPreserveUnknownFieldsAndRestoreExactly() throws Exception {
        NativeUiState before = replace(sample(),"temp_2","1##0##unknown##");
        NativeHotkeys original = NativeHotkeys.decode(before.text("Hotkeys"));
        Map<String,Boolean> changes = new LinkedHashMap<>(), restore = new LinkedHashMap<>();
        for (Map<String,Object> field : NativeUiSwitches.inspect(before)) {
            assertEquals(true,field.get("available"));
            boolean value=(Boolean)field.get("value"); changes.put((String)field.get("key"),!value); restore.put((String)field.get("key"),value);
        }
        assertEquals(18,changes.size());
        NativeUiState changed = NativeUiSwitches.apply(before,changes);
        assertArrayEquals(before.get("temp_3"),changed.get("temp_3"));
        assertEquals("0##1##unknown##",changed.text("temp_2"));
        NativeHotkeys decoded = NativeHotkeys.decode(changed.text("Hotkeys"));
        Set<Integer> affected=new HashSet<>(Arrays.asList(20,26,51,55,56,60,69,70,71,74,89,99,101,102,125,126));
        for(int i=0;i<original.size();i++) if(!affected.contains(i)) assertArrayEquals(original.raw(i),decoded.raw(i));
        assertEquals(before.revision(),NativeUiSwitches.apply(changed,restore).revision());
    }
    @Test public void noncanonicalMissingAndUnknownValuesAreNotInitialized() throws Exception {
        NativeUiState state = sample();
        String malformed=NativeHotkeys.decode(state.text("Hotkeys")).withText(Collections.singletonMap(20,"01")).encode();
        NativeUiState bad = replace(state,"Hotkeys",malformed);
        reject(409,()->NativeUiSwitches.apply(bad,Collections.singletonMap("healthPercent",true)));
        NativeUiState shortLayout=replace(state,"temp_2","1");
        reject(409,()->NativeUiSwitches.apply(shortLayout,Collections.singletonMap("bloodLayout",false)));
        for(Map<String,Boolean> patch:Arrays.asList(Collections.singletonMap("arbitrary",true),Collections.singletonMap("bloodLayout",(Boolean)null),Collections.<String,Boolean>emptyMap()))
            reject(400,()->NativeUiSwitches.apply(state,patch));
        Map<String,Boolean> mixed=new LinkedHashMap<>();mixed.put("bloodLayout",false);mixed.put("healthPercent",true);
        reject(409,()->NativeUiSwitches.apply(bad,mixed));
        assertEquals("1##0",state.text("temp_2"));
    }
    @Test public void switchingOneControlDoesNotNormalizeOtherState() throws Exception {
        NativeUiState state=sample();
        boolean current=(Boolean)NativeUiSwitches.inspect(state).get(0).get("value");
        assertEquals(state.revision(),NativeUiSwitches.apply(state,Collections.singletonMap("healthPercent",current)).revision());
        NativeUiState changed=NativeUiSwitches.apply(state,Collections.singletonMap("healthPercent",!current));
        assertEquals(Collections.singletonList("Hotkeys"),state.changedFields(changed));
    }
    @Test public void legacyZeroIsReadAsNativeFalseButNotNormalizedOnNoChange() throws Exception {
        NativeUiState state=sample();
        state=replace(state,"Hotkeys",NativeHotkeys.decode(state.text("Hotkeys")).withText(Collections.singletonMap(20,"0")).encode());
        assertEquals(false,NativeUiSwitches.inspect(state).get(0).get("value"));
        assertEquals(state.revision(),NativeUiSwitches.apply(state,Collections.singletonMap("healthPercent",false)).revision());
        NativeUiState changed=NativeUiSwitches.apply(state,Collections.singletonMap("healthPercent",true));
        assertEquals("真",NativeHotkeys.decode(changed.text("Hotkeys")).text(20));
    }
    private void reject(int status,Runnable action) {
        try { action.run();fail("Expected rejection"); } catch(DofFailure e) { assertEquals(status,e.status); }
    }
}
