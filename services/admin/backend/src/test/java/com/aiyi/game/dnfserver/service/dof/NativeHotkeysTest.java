package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeHotkeysTest {
    private String fixture(String name) throws Exception {
        JsonNode root = new ObjectMapper().readTree(PrivateNativeFixtures.open("ui-native-100.json"));
        return root.get(name).get("Hotkeys").asText();
    }
    @Test public void realNativeCapturesRoundTripAndDecodeGbk() throws Exception {
        NativeHotkeys before = NativeHotkeys.decode(fixture("before"));
        NativeHotkeys after = NativeHotkeys.decode(fixture("after"));
        assertEquals(100, before.size()); assertEquals(138, after.size());
        assertEquals(fixture("before"), before.encode()); assertEquals(fixture("after"), after.encode());
        assertEquals("真", after.text(0)); assertEquals("普通级", after.text(33));
        assertEquals("248", after.text(27)); assertEquals("", after.text(137));
    }
    @Test public void editsOnlySpecifiedFieldsAndRetainsUnknownTail() throws Exception {
        NativeHotkeys input = NativeHotkeys.decode(fixture("after"));
        Map<Integer,String> edits = new LinkedHashMap<>(); edits.put(27, "00249"); edits.put(33, "测试难度");
        NativeHotkeys changed = NativeHotkeys.decode(input.withText(edits).encode());
        assertEquals(input.size(), changed.size());
        for (int i = 0; i < input.size(); i++) if (!edits.containsKey(i)) assertArrayEquals(input.raw(i), changed.raw(i));
        assertEquals("00249", changed.text(27)); assertEquals("测试难度", changed.text(33));
        assertEquals(fixture("after"), input.encode());
        byte[] copy = input.raw(0); copy[0] = 0; assertEquals("真", input.text(0));
        assertEquals(fixture("after"), changed.withText(new HashMap<Integer,String>() {{ put(27, input.text(27)); put(33, input.text(33)); }}).encode());
    }
    @Test public void malformedAndUnrepresentableEditsFailWithoutMutation() throws Exception {
        NativeHotkeys input = NativeHotkeys.decode(fixture("before"));
        for (String bad : Arrays.asList("A", "00 00", "XX", "", fixture("before").toLowerCase(Locale.ROOT))) {
            try { NativeHotkeys.decode(bad); fail(); } catch (IllegalArgumentException expected) { }
        }
        for (String bad : Arrays.asList("x*y", "x\0y", "🙂")) {
            try { input.withText(Collections.singletonMap(27, bad)); fail(); } catch (IllegalArgumentException expected) { }
        }
        for (int bad : new int[]{-1, 100}) {
            try { input.withText(Collections.singletonMap(bad, "1")); fail(); } catch (IllegalArgumentException expected) { }
        }
        assertEquals(fixture("before"), input.encode());
    }
    @Test public void unknownNonTextBytesSurviveKnownFieldEdit() throws Exception {
        String wire = fixture("after");
        byte[] key = new byte[]{(byte)0xb7,(byte)0xa2,(byte)0xc8,(byte)0xf6,(byte)0xca,(byte)0xc7,(byte)0xb7,(byte)0xf1,(byte)0xb0,(byte)0xa2,(byte)0xc8,(byte)0xf8,(byte)0xb8,(byte)0xc1,103,103,100,97,103,97,101,104,101,97,104,97,101,97,104,98,97,104,97,98};
        byte[] bytes = new byte[wire.length()/2];
        for(int i=0;i<bytes.length;i++) bytes[i]=(byte)Integer.parseInt(wire.substring(i*2,i*2+2),16);
        byte[] raw = NativeConfigContainer.cipher(bytes,key); raw=Arrays.copyOf(raw,raw.length+1); raw[raw.length-1]=(byte)0xff;
        StringBuilder hex=new StringBuilder(); for(byte b:NativeConfigContainer.cipher(raw,key)) hex.append(String.format("%02X",b&255));
        NativeHotkeys unknown=NativeHotkeys.decode(hex.toString());
        assertEquals(hex.toString(),unknown.encode());
        NativeHotkeys changed=NativeHotkeys.decode(unknown.withText(Collections.singletonMap(27,"250")).encode());
        assertArrayEquals(new byte[]{(byte)0xff},changed.raw(137));
        try { changed.text(137); fail(); } catch(IllegalArgumentException expected) { }
    }
}
