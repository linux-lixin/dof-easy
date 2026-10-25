package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class NativeConfigContainerTest {
    private static void assertThrows(Class<? extends Throwable> type, Runnable operation) {
        try { operation.run(); fail("Expected rejected native data"); }
        catch (RuntimeException e) { if (!type.isInstance(e)) throw e; }
    }
    private static byte[] b(String s) { return s.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static NativeConfigContainer.Document document(boolean compressed, boolean password) {
        byte[] binary = new byte[256]; for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;
        return new NativeConfigContainer.Document(Arrays.asList(
            new NativeConfigContainer.Entry(b("unknown"), binary, b("NEW")),
            new NativeConfigContainer.Entry(b("unknown"), new byte[0], new byte[0])), compressed, password);
    }
    @Test public void nativeCipherVectors() {
        assertArrayEquals(new byte[]{2,5,7,13,13,23,31,40}, NativeConfigContainer.cipher(new byte[8], new byte[0]));
        assertArrayEquals(new byte[]{(byte)0xbb,(byte)0xf3,0x16,(byte)0xe8,(byte)0xd9,0x40,(byte)0xaf,0x0a,(byte)0xd3},
            NativeConfigContainer.cipher(b("Plaintext"), b("Key")));
    }
    @Test public void preservesUnknownBinaryAndDuplicateFieldsInAllModes() {
        for (boolean compressed : new boolean[]{false,true}) for (byte[] password : new byte[][]{new byte[0], b("test-only")}) {
            NativeConfigContainer.Document doc = document(compressed, password.length > 0);
            assertEquals(doc, NativeConfigContainer.decode(NativeConfigContainer.encode(doc, password), password));
        }
    }
    @Test public void malformedFlagsWrongPasswordAndTrailingCompressedDataRejected() {
        byte[] wire = NativeConfigContainer.encode(document(true, false), new byte[0]);
        byte[] flags = wire.clone(); flags[14] = 2;
        byte[] tail = Arrays.copyOf(wire, wire.length + 1);
        for (byte[] bad : new byte[][]{new byte[0], Arrays.copyOf(wire,15), flags, tail, Arrays.copyOf(wire,wire.length-1)})
            assertThrows(IllegalArgumentException.class, () -> NativeConfigContainer.decode(bad, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> NativeConfigContainer.decode(wire, b("unexpected")));
        byte[] protectedWire = NativeConfigContainer.encode(document(true,true), b("test"));
        assertThrows(IllegalArgumentException.class, () -> NativeConfigContainer.decode(protectedWire, b("wrong")));
    }
    @Test public void nativeEmptyDocumentHasNoZlibStream() {
        NativeConfigContainer.Document empty = new NativeConfigContainer.Document(Collections.emptyList(),true,false);
        byte[] wire = NativeConfigContainer.encode(empty,new byte[0]);
        assertEquals(24,wire.length); assertEquals(empty,NativeConfigContainer.decode(wire,new byte[0]));
    }
    @Test public void declaredDecompressionLengthIsEnforced() {
        byte[] wire = NativeConfigContainer.encode(document(true,false),new byte[0]);
        byte[] body = NativeConfigContainer.cipher(Arrays.copyOfRange(wire,16,wire.length),new byte[0]);
        for (int length : new int[]{-1,NativeConfigContainer.MAX_BYTES+1,1}) {
            java.nio.ByteBuffer.wrap(body).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4,length);
            byte[] changed=wire.clone();System.arraycopy(NativeConfigContainer.cipher(body,new byte[0]),0,changed,16,body.length);
            assertThrows(IllegalArgumentException.class,()->NativeConfigContainer.decode(changed,new byte[0]));
        }
    }
    @Test public void entriesAreImmutableAndDoNotPrintSecrets() {
        byte[] name = b("secret-name"), value = b("secret-value");
        NativeConfigContainer.Entry e = new NativeConfigContainer.Entry(name,value,b("TXT"));
        name[0] = 0; value[0] = 0; e.value()[0] = 0;
        assertArrayEquals(b("secret-value"),e.value()); assertArrayEquals(b("secret-name"),e.name());
        assertFalse(e.toString().contains("secret-value"));
    }
    /** Optional private fixtures, required by the documented real-sample check command. */
    @Test public void suppliedNativeFixturesAreByteIdenticalAfterJavaRegeneration() throws Exception {
        String paths = System.getProperty("dof.native.fixtures", "");
        org.junit.Assume.assumeFalse("Private native fixtures not configured", paths.isEmpty());
        for (String path : paths.split(java.io.File.pathSeparator)) {
            byte[] original = Files.readAllBytes(Paths.get(path));
            NativeConfigContainer.Document doc = NativeConfigContainer.decode(original, new byte[0]);
            assertEquals(7, doc.entries.size());
            byte[] generated = NativeConfigContainer.encode(doc,new byte[0]);
            assertArrayEquals(original,generated);
            assertEquals(doc,NativeConfigContainer.decode(generated,new byte[0]));
        }
    }
}
