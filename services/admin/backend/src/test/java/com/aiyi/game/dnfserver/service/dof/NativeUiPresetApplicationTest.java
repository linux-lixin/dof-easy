package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeUiPresetApplicationTest {
    private Map<String,String> sample(String name) throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        try(InputStream in=PrivateNativeFixtures.open("ui-native-100.json")) {
            return mapper.convertValue(mapper.readTree(in).get(name),new TypeReference<Map<String,String>>() {});
        }
    }
    @Test public void native100LayoutMatchesButLegacyGeneralSaveStillHasUnreplicatedEffects() throws Exception {
        NativeUiState before=NativeUiState.fromText(sample("before")), after=NativeUiState.fromText(sample("after"));
        NativeUiState candidate=new NativeUiPresetApplication().candidate("native-100",before);
        assertArrayEquals(after.get("temp_2"),candidate.get("temp_2"));
        assertArrayEquals(after.get("temp_3"),candidate.get("temp_3"));
        NativeHotkeys actual=NativeHotkeys.decode(candidate.text("Hotkeys")), saved=NativeHotkeys.decode(after.text("Hotkeys"));
        for(int index:new int[]{27,28,29,30,72,73,75,76,77,78}) assertEquals(saved.text(index),actual.text(index));
        // This is why arbitrary legacy states are not migrated: the
        // historical general Save expanded 100 fields to 138 and normalized
        // unrelated options. Do not silently synthesize those fields here.
        assertEquals(100,actual.size()); assertEquals(138,saved.size());
        assertNotEquals(after.revision(),candidate.revision());
    }
    @Test public void allNativeApplicationsPreserveExtensionsAndAreIdempotent() throws Exception {
        NativeUiPresetApplication codec=new NativeUiPresetApplication();
        Map<String,String> values=sample("after");
        values.put("temp_2",values.get("temp_2")+"##future-switch");
        String[] sections=values.get("temp_3").split("##",-1);
        sections[0]+="&future-blood"; sections[1]+="&future-create";
        values.put("temp_3",String.join("##",sections)+"##future-section");
        NativeUiState original=NativeUiState.fromText(values);
        for(Map<String,Object> row:new NativeUiPresetCatalog().list()) {
            NativeUiState candidate=codec.candidate((String)row.get("id"),original);
            assertEquals(candidate.encode(),codec.candidate((String)row.get("id"),candidate).encode());
            assertEquals(values.get("temp_2"),candidate.text("temp_2"));
            String[] changed=candidate.text("temp_3").split("##",-1);
            assertTrue(changed[0].endsWith("&future-blood")); assertEquals(sections[1],changed[1]);
            assertEquals("future-section",changed[2]);
            NativeHotkeys a=NativeHotkeys.decode(original.text("Hotkeys")),b=NativeHotkeys.decode(candidate.text("Hotkeys"));
            Set<Integer> coordinates=new HashSet<>(Arrays.asList(27,28,29,30,66,67,72,73,75,76,77,78));
            assertEquals(a.size(),b.size());
            for(int i=0;i<a.size();i++) if(!coordinates.contains(i)) assertEquals(a.text(i),b.text(i));
        }
    }
    @Test public void missingAndMalformedLayoutsFailWithoutChangingSource() throws Exception {
        NativeUiPresetApplication codec=new NativeUiPresetApplication();
        for(String bad:new String[]{null,"","0##0","not-an-integer"+sample("after").get("temp_3")}) {
            Map<String,String> values=sample("after");values.put("temp_3",bad);
            NativeUiState state=NativeUiState.fromText(values);String revision=state.revision();
            try {codec.candidate("native-60",state);fail();} catch(DofFailure expected) {assertEquals(409,expected.status);}
            assertEquals(revision,state.revision());
        }
    }
    @Test public void publicApplicationRejectsUnmigratedLegacyAndPreservesCurrentCustomSettings() throws Exception {
        NativeUiPresetApplication codec=new NativeUiPresetApplication();
        NativeUiState legacy=NativeUiState.fromText(sample("before"));
        assertFalse(codec.compatible(legacy));
        try {codec.apply("native-60",legacy);fail();} catch(DofFailure expected) {assertEquals(409,expected.status);}
        NativeUiState current=NativeUiState.fromText(sample("after"));
        current=NativeUiCoordinates.apply(current,Collections.singletonMap("skillBar8X",123));
        assertTrue(codec.compatible(current));
        NativeUiState changed=codec.apply("native-60",current);
        assertEquals("123",NativeHotkeys.decode(changed.text("Hotkeys")).text(77));
        assertEquals(changed.revision(),codec.apply("native-60",changed).revision());
    }
}
