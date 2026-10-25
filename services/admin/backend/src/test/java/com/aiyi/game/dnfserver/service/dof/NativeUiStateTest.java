package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class NativeUiStateTest {
    private Map<String,String> sample(String which) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> doc = mapper.readValue(PrivateNativeFixtures.open("ui-native-100.json"), new TypeReference<Map<String,Object>>() {});
        return mapper.convertValue(doc.get(which), new TypeReference<Map<String,String>>() {});
    }
    @Test public void nativeSamplesRoundTripWithoutTouchingOpaqueBytes() throws Exception {
        for (String which : Arrays.asList("before", "after")) {
            Map<String,String> original = sample(which);
            NativeUiState state = NativeUiState.fromText(original);
            NativeUiState decoded = NativeUiState.decode(state.encode());
            assertEquals(state.revision(), decoded.revision());
            for (String key : NativeUiState.COLUMNS) assertEquals(original.get(key), decoded.text(key));
        }
        NativeUiState before = NativeUiState.fromText(sample("before"));
        NativeUiState after = NativeUiState.fromText(sample("after"));
        assertEquals(NativeUiState.COLUMNS, before.changedFields(after));
        assertFalse((Boolean)before.inspect().get("bloodLayoutEnabled"));
        assertTrue((Boolean)after.inspect().get("bloodLayoutEnabled"));
        assertFalse((Boolean)after.inspect().get("createRoleLayoutEnabled"));
    }
    @Test public void keepsNullEmptyUnknownSectionsAndOriginalNumericFormattingDistinct() {
        Map<String,byte[]> bytes = new LinkedHashMap<>();
        bytes.put("Hotkeys", null); bytes.put("temp_2", new byte[0]);
        bytes.put("temp_3", "001&-0&&##future##".getBytes(StandardCharsets.UTF_8));
        NativeUiState state = new NativeUiState(bytes);
        assertEquals(state.revision(), NativeUiState.decode(state.encode()).revision());
        assertEquals(Arrays.asList("001&-0&&", "future", ""), state.inspect().get("coordinateSections"));
        bytes.put("Hotkeys", new byte[0]); assertNotEquals(state.revision(), new NativeUiState(bytes).revision());
    }
    @Test public void doesNotSilentlyReplaceMalformedUtf8() {
        Map<String,byte[]> bytes = new LinkedHashMap<>();
        bytes.put("Hotkeys", new byte[]{(byte)0xff}); bytes.put("temp_2", null); bytes.put("temp_3", null);
        NativeUiState state = new NativeUiState(bytes);
        assertArrayEquals(new byte[]{(byte)0xff}, NativeUiState.decode(state.encode()).get("Hotkeys"));
        try { state.text("Hotkeys"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void doesNotOverwriteUnrelatedHotkeysOrUnknownCoordinateExtensions() throws Exception {
        NativeUiProfiles profiles = new NativeUiProfiles();
        NativeUiState old = NativeUiState.fromText(sample("before"));
        NativeUiState after = profiles.apply("native-100", old);
        assertTrue(profiles.is100(after)); assertEquals(after.revision(), profiles.apply("native-100", after).revision());
        for (String key : NativeUiState.COLUMNS) {
            Map<String,String> changed = sample("before"); changed.put(key, changed.get(key) + "extra");
            NativeUiState state = NativeUiState.fromText(changed); assertFalse(profiles.compatible(state));
            try { profiles.apply("native-100", state); fail(); } catch (DofFailure expected) { assertEquals(409, expected.status); }
        }
        try { profiles.apply("native-110", old); fail(); } catch (DofFailure expected) { assertEquals(409, expected.status); }
    }
    @Test public void scopeNeverInheritsOldExclusionsAsCompletion() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> scope = mapper.readValue(getClass().getResourceAsStream("/dof/scope.json"), new TypeReference<Map<String,Object>>() {});
        List<Map<String,Object>> groups = mapper.convertValue(scope.get("groups"), new TypeReference<List<Map<String,Object>>>() {});
        Set<String> ids = new HashSet<>(); int count = 0;
        for (Map<String,Object> group : groups) {
            List<Map<String,Object>> operations = mapper.convertValue(group.get("operations"), new TypeReference<List<Map<String,Object>>>() {});
            for (Map<String,Object> op : operations) {
                assertTrue(ids.add(op.get("id").toString())); assertEquals(true, op.get("included"));
                assertEquals("pending_equivalence", op.get("status")); count++;
            }
        }
        assertTrue(groups.size() >= 71); assertTrue(count >= 209); assertTrue(ids.contains("feature-016-01"));
        assertEquals(scope.get("discoveredOperations"), count);
        assertTrue(ids.contains("feature-015-23"));
    }
}
