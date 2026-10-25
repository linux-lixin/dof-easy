package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeScriptContextTest {
    private static final String PIN = NativeScriptContext.CLIENT_DLL_SHA256;

    @Test public void authorizedNativeSampleRegeneratesEveryByteWhenProvided() throws Exception {
        String path = System.getProperty("dof.script.privateParameters");
        org.junit.Assume.assumeNotNull(path);
        JsonNode sample = new ObjectMapper().readTree(new File(path));
        String encoded = new String(hex(sample.get("contentKeyParameterHex").asText()), StandardCharsets.US_ASCII);
        byte[] decoded = NativeScriptContext.decode(encoded, PIN);
        assertTrue("Sample payload must not be empty", decoded.length > 0);
        assertTrue("Entire native parameter must regenerate", encoded.equals(NativeScriptContext.encode(decoded, PIN)));
        // Do not include either native value in assertion diagnostics or snapshots.
    }

    @Test public void unknownVersionAndWrongContextAreRejected() {
        for (String version : new String[]{null, "", "another-dll"}) {
            reject(() -> NativeScriptContext.encode(new byte[]{'A'}, version));
            reject(() -> NativeScriptContext.decode("000000000", version));
        }
        String other = NativeTextEnvelope.encode(new byte[]{'A'}, new byte[]{'B'});
        reject(() -> NativeScriptContext.decode(other, PIN));
    }

    @Test public void opaqueFieldsAndGbkBytesAreNotNormalized() {
        byte[] value = "unknown##00042##字段".getBytes(java.nio.charset.Charset.forName("GBK"));
        String encoded = NativeScriptContext.encode(value, PIN);
        byte[] decoded = NativeScriptContext.decode(encoded, PIN);
        assertArrayEquals(value, decoded);
        decoded[0] = 0;
        assertArrayEquals(value, NativeScriptContext.decode(encoded, PIN));
        reject(() -> NativeScriptContext.encode(new byte[]{0}, PIN));
    }

    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(text.substring(i*2, i*2+2), 16);
        return bytes;
    }
    private static void reject(Runnable run) {
        try { run.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { }
    }
}
