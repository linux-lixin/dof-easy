package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeTextEnvelopeTest {
    @Test public void nativeWriterVectorsWhenProvided() throws Exception {
        String path = System.getProperty("dof.script.parameterVectors");
        org.junit.Assume.assumeNotNull(path);
        JsonNode vectors = new ObjectMapper().readTree(new File(path)).get("vectors");
        assertEquals(12, vectors.size());
        for (JsonNode v : vectors) {
            byte[] plain = hex(v.get("plainHex").asText()), context = hex(v.get("contextHex").asText());
            String expected = v.get("nativeEnvelope").asText();
            assertEquals(expected, NativeTextEnvelope.encode(plain, context));
            assertArrayEquals(plain, NativeTextEnvelope.decode(expected, context));
        }
    }

    @Test public void contextMismatchAndMalformedParametersFailClosed() {
        byte[] context = ascii("test context");
        String valid = NativeTextEnvelope.encode(ascii("0##12345678"), context);
        reject(() -> NativeTextEnvelope.decode(valid, ascii("different context")));
        for (String bad : new String[]{null, "", "12345678", "1234567890", "ggggggggg",
                valid.toLowerCase(java.util.Locale.ROOT), "10" + valid.substring(valid.length()-9),
                repeat('A', 2061), "１２３４５６７８９"})
            reject(() -> NativeTextEnvelope.inspect(bad));
        reject(() -> NativeTextEnvelope.encode(new byte[]{0}, context));
        reject(() -> NativeTextEnvelope.encode(ascii("x"), new byte[]{0}));
        reject(() -> NativeTextEnvelope.encode(new byte[1025], context));
    }

    @Test public void inspectionIsUnverifiedAndTagIsNotPayloadAuthentication() {
        byte[] context = ascii("example");
        String encoded = NativeTextEnvelope.encode(ascii("A"), context);
        NativeTextEnvelope.Inspection inspected = NativeTextEnvelope.inspect(encoded);
        assertFalse(inspected.contextVerified());
        byte[] modifiedCopy = inspected.payload(); modifiedCopy[0] = 'B';
        assertArrayEquals(ascii("A"), inspected.payload());
        assertFalse(inspected.toString().contains(encoded));
        // Native suffix is context-only. A changed payload remains decodable;
        // callers must supply a separate file hash/signature for integrity.
        assertArrayEquals(ascii("B"), NativeTextEnvelope.decode("52" + encoded.substring(2), context));
    }

    @Test public void approvedRealSampleExplainsIndexMaskWhenProvided() throws Exception {
        String path = System.getProperty("dof.script.privateParameters");
        org.junit.Assume.assumeNotNull(path);
        JsonNode v = new ObjectMapper().readTree(new File(path));
        assertEquals("e34592496e8d43679bb18beb4287d0c7c50a08b0cff9d088e16993981825db57",
                v.get("nativePvfSha256").asText());
        NativeTextEnvelope.Inspection parsed = NativeTextEnvelope.inspect(
                new String(hex(v.get("contentKeyParameterHex").asText()), StandardCharsets.US_ASCII));
        String[] fields = new String(parsed.payload(), StandardCharsets.US_ASCII).split("##", -1);
        assertTrue("Native sample shape", fields.length == 2 && fields[0].equals("0")
                && fields[1].matches("[0-9]+"));
        int extra = Integer.parseInt(fields[1]);
        assertTrue("Sample parameter agrees with all-index observation", (extra ^ 0xd4bd141e) == 0xd6f855e2);
        assertFalse(parsed.contextVerified());
    }

    private static byte[] ascii(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
    private static byte[] hex(String s) {
        byte[] b = new byte[s.length()/2];
        for (int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    private static String repeat(char c,int n) { char[] a=new char[n];java.util.Arrays.fill(a,c);return new String(a); }
    private static void reject(Runnable run) {
        try { run.run();fail("Expected parameter rejection"); } catch (IllegalArgumentException expected) { }
    }
}
