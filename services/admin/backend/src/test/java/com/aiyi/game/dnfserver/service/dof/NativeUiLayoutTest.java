package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeUiLayoutTest {
    private NativeUiState state(String layout) {
        Map<String,String> fields=new LinkedHashMap<>();
        fields.put("Hotkeys","opaque untouched field");fields.put("temp_2","1##0##future");fields.put("temp_3",layout);
        return NativeUiState.fromText(fields);
    }
    private List<String> nativeLayouts() throws Exception {
        JsonNode root=new ObjectMapper().readTree(PrivateNativeFixtures.open("layout-native-vectors.json"));
        assertEquals("0x7f1a74",root.get("nativeEntry").asText());
        List<String> values=new ArrayList<>();for(JsonNode n:root.get("layouts"))values.add(n.asText());return values;
    }
    @Test public void all132FieldsMatchOriginalSaveSqlIncludingSignedBoundaries() throws Exception {
        List<String> vectors=nativeLayouts();NativeUiState before=state(vectors.get(0));
        for(String layout:vectors) {
            Map<String,Integer> changes=new LinkedHashMap<>();String[] sections=layout.split("##");
            for(int s=0;s<2;s++) {
                String[] values=sections[s].split("&");
                for(int i=0;i<values.length;i++)changes.put((s==0?"blood.":"create.")+i,Integer.valueOf(values[i]));
            }
            assertEquals(132,changes.size());NativeUiState after=NativeUiLayout.candidate(before,changes);
            assertEquals(layout,after.text("temp_3"));assertEquals(changes,NativeUiLayout.read(after));
            assertArrayEquals(before.get("Hotkeys"),after.get("Hotkeys"));assertArrayEquals(before.get("temp_2"),after.get("temp_2"));
        }
    }
    @Test public void partialChangePreservesUnknownFieldsAndNoOpSpelling() throws Exception {
        String[] sections=nativeLayouts().get(0).split("##");
        sections[0]="0007"+sections[0].substring(sections[0].indexOf('&'))+"&opaque&";
        NativeUiState original=state(sections[0]+"##"+sections[1]+"&creation-extension##extra##");
        assertEquals(original.revision(),NativeUiLayout.candidate(original,Collections.singletonMap("blood.0",7)).revision());
        NativeUiState changed=NativeUiLayout.candidate(original,Collections.singletonMap("create.21",-12));
        assertTrue(changed.text("temp_3").startsWith(sections[0]+"##"));
        assertTrue(changed.text("temp_3").endsWith("&-12&creation-extension##extra##"));
        assertEquals(Collections.singletonList("temp_3"),original.changedFields(changed));
    }
    @Test public void malformedExistingKnownFieldsAndUnknownEditTargetsFailWithoutChangingSource() throws Exception {
        String valid=nativeLayouts().get(0);
        for(String bad:Arrays.asList("", "1##2", valid.replaceFirst("[0-9]+","2147483648"),valid.replace("##","##bad&"))) {
            NativeUiState input=state(bad);String revision=input.revision();
            try{NativeUiLayout.candidate(input,Collections.singletonMap("blood.0",1));fail();}catch(DofFailure expected){}
            assertEquals(revision,input.revision());
        }
        NativeUiState input=state(valid);String revision=input.revision();
        for(String bad:Arrays.asList("blood.110","create.22","blood.01","blood.-1","temp_3","blood.0;DROP TABLE")) {
            try{NativeUiLayout.candidate(input,Collections.singletonMap(bad,1));fail();}catch(DofFailure expected){}
        }
        assertEquals(revision,input.revision());
    }
    @Test public void nativeDecimalSpacingIsPreservedWhileOtherCoordinatesStayIntegers() throws Exception {
        String nativeLayout=new ObjectMapper().readTree(PrivateNativeFixtures.open("layout-native-spacing-vector.json")).get("layout").asText();
        NativeUiState before=state(nativeLayout);
        assertEquals(new BigDecimal("5.2"),NativeUiLayout.read(before).get("blood.40"));
        assertEquals(before.revision(),NativeUiLayout.edit(before,Collections.singletonMap("blood.40",new BigDecimal("5.200"))).revision());
        NativeUiState after=NativeUiLayout.edit(before,Collections.singletonMap("blood.40",new BigDecimal("6.25")));
        assertEquals(nativeLayout.replace("&5.2&","&6.25&"),after.text("temp_3"));
        assertArrayEquals(before.get("Hotkeys"),after.get("Hotkeys"));
        for(String key:Arrays.asList("blood.39","create.0")) {
            try {NativeUiLayout.edit(before,Collections.singletonMap(key,new BigDecimal("5.2")));fail();}
            catch(DofFailure expected) {assertEquals(400,expected.status);}
        }
        try {NativeUiLayout.edit(before,Collections.singletonMap("blood.40",new BigDecimal("5.123456789")));fail();}
        catch(DofFailure expected) {assertEquals(400,expected.status);}
        List<Map<String,Object>> controls=NativeUiLayoutControls.inspect(before);assertEquals(132,controls.size());
        assertEquals(1,controls.stream().filter(c->Boolean.TRUE.equals(c.get("decimal"))).count());
    }
}
