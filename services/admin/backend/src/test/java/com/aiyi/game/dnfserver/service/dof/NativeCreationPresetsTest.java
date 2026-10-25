package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCreationPresetsTest {
    private NativeUiState sample() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        Map<String,String> fields=mapper.convertValue(mapper.readTree(PrivateNativeFixtures.open("ui-native-100.json")).get("after"),new TypeReference<Map<String,String>>(){});
        String[] sections=fields.get("temp_3").split("##",-1);
        sections[0]+="&future-blood";sections[1]+="&future-create";
        fields.put("temp_3",String.join("##",sections)+"##future-section");
        return NativeUiState.fromText(fields);
    }
    @Test public void allNativePresetsOnlyChangeCreationCoordinatesAndPreserveExtensions() throws Exception {
        NativeCreationPresets presets=new NativeCreationPresets();NativeUiState original=sample();
        assertEquals(3,presets.list().size());
        for(Map<String,Object> row:presets.list()) {
            String id=(String)row.get("id");NativeUiState changed=presets.apply(id,original);
            assertArrayEquals(original.get("Hotkeys"),changed.get("Hotkeys"));
            assertArrayEquals(original.get("temp_2"),changed.get("temp_2"));
            assertEquals(original.text("temp_3").split("##",-1)[0],changed.text("temp_3").split("##",-1)[0]);
            assertTrue(changed.text("temp_3").endsWith("&future-create##future-section"));
            assertEquals(changed.revision(),presets.apply(id,changed).revision());
            int first=NativeUiLayout.read(changed).get("create.0").intValue();
            assertEquals(id.equals("creation-60")?204:id.equals("creation-85")?70:80,first);
        }
    }
    @Test public void previewImagesAreNativePngWithPinnedDigestsAndNoInternalCatalogFields() {
        NativeCreationPresets presets=new NativeCreationPresets();
        for(Map<String,Object> row:presets.list()) {
            byte[] image=presets.image(row.get("id").toString());
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},Arrays.copyOf(image,8));
            assertEquals(row.get("sha256"),NativeUiState.sha256(image));
            assertFalse(row.containsKey("handler"));assertFalse(row.containsKey("nativeGuiWrites"));
        }
        try {presets.image("../../creation-60");fail();}catch(DofFailure e){assertEquals(404,e.status);}
    }
    @Test public void malformedLayoutsAreNeverInitializedByPreset() throws Exception {
        NativeUiState original=sample();Map<String,byte[]> fields=new LinkedHashMap<>();
        for(String key:NativeUiState.COLUMNS)fields.put(key,original.get(key));fields.put("temp_3",null);
        NativeUiState incomplete=new NativeUiState(fields);
        try {new NativeCreationPresets().apply("creation-100",incomplete);fail();}catch(DofFailure e){assertEquals(409,e.status);}
        assertNull(incomplete.get("temp_3"));
    }
}
