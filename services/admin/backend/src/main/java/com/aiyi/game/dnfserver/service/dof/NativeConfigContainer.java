package com.aiyi.game.dnfserver.service.dof;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;

/** Pinned 2.8 native container codec. Does not expose credentials or publish artifacts. */
public final class NativeConfigContainer {
    private NativeConfigContainer() {}
    static final int MAX_BYTES = 16 * 1024 * 1024, MAX_RECORDS = 4096;
    private static final int COMPRESSION_MAGIC = 0x033e0f0d;
    private static final byte[] MAGIC = hex("c005d4eba402d0adc5ce06cece05");
    private static final byte[] VERSION = "花枝5.0".getBytes(Charset.forName("GBK"));
    private static final byte[] FIELD = join(hex("cdb5c4e3c2e9b1d4d4b4c2ebb0a1a3acb2bbbbe1d7d4bcbac8a5d0b4c2efa3bf"), VERSION);
    private static final byte[] ROW = join(hex("0f74d3848a"), VERSION);

    public static final class Entry {
        private final byte[] name, value, kind;
        public Entry(byte[] name, byte[] value, byte[] kind) {
            this.name = name.clone(); this.value = value.clone(); this.kind = kind.clone();
        }
        public byte[] name() { return name.clone(); }
        public byte[] value() { return value.clone(); }
        public byte[] kind() { return kind.clone(); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry e = (Entry) other;
            return Arrays.equals(name, e.name) && Arrays.equals(value, e.value) && Arrays.equals(kind, e.kind);
        }
        @Override public int hashCode() { return Objects.hash(Arrays.hashCode(name), Arrays.hashCode(value), Arrays.hashCode(kind)); }
    }
    public static final class Document {
        public final List<Entry> entries;
        public final boolean compressed, passwordRequired;
        public Document(List<Entry> entries, boolean compressed, boolean passwordRequired) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.compressed = compressed; this.passwordRequired = passwordRequired;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Document)) return false;
            Document d = (Document) other;
            return compressed == d.compressed && passwordRequired == d.passwordRequired && entries.equals(d.entries);
        }
        @Override public int hashCode() { return Objects.hash(entries, compressed, passwordRequired); }
    }
    private static IllegalArgumentException invalid() {
        // Never include input bytes, names, credentials or native keys in error messages.
        return new IllegalArgumentException("晴空原生配置格式、口令或长度不兼容");
    }
    public static Document decode(byte[] wire, byte[] password) {
        if (wire.length > MAX_BYTES || wire.length < 16 || !Arrays.equals(MAGIC, Arrays.copyOf(wire, 14))
            || wire[14] < 0 || wire[14] > 1 || wire[15] < 0 || wire[15] > 1
            || (wire[15] == 1) != (password.length > 0)) throw invalid();
        byte[] raw = cipher(Arrays.copyOfRange(wire, 16, wire.length), password);
        if (wire[14] == 1) raw = inflate(raw);
        List<Entry> entries = new ArrayList<>();
        if (raw.length != 0) {
            for (byte[] row : split(raw, ROW, MAX_RECORDS)) {
                List<byte[]> fields = split(row, FIELD, 3);
                if (fields.size() != 3) throw invalid();
                entries.add(new Entry(fields.get(0), fields.get(1), fields.get(2)));
            }
        }
        return new Document(entries, wire[14] == 1, wire[15] == 1);
    }
    public static byte[] encode(Document document, byte[] password) {
        if (document.passwordRequired != (password.length > 0) || document.entries.size() > MAX_RECORDS) throw invalid();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean first = true;
        for (Entry entry : document.entries) {
            if (!first) append(out, ROW);
            first = false;
            byte[][] fields = { entry.name, entry.value, entry.kind };
            for (int i = 0; i < fields.length; i++) {
                if (indexOf(fields[i], FIELD, 0) >= 0 || indexOf(fields[i], ROW, 0) >= 0) throw invalid();
                if (i > 0) append(out, FIELD);
                append(out, fields[i]);
            }
        }
        byte[] raw = out.toByteArray();
        if (document.compressed) raw = deflate(raw);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        append(wire, MAGIC); wire.write(document.compressed ? 1 : 0); wire.write(document.passwordRequired ? 1 : 0);
        append(wire, cipher(raw, password));
        return wire.toByteArray();
    }
    /** Native algorithm 2. Empty passwords intentionally skip the key schedule. */
    static byte[] cipher(byte[] data, byte[] key) {
        for (byte b : key) if (b == 0) throw invalid();
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        if (key.length != 0) for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 255)) & 255;
            int temp = s[i]; s[i] = s[j]; s[j] = temp;
        }
        byte[] out = new byte[data.length];
        int i = 0; j = 0;
        for (int n = 0; n < data.length; n++) {
            i = (i + 1) & 255; j = (j + s[i]) & 255;
            int temp = s[i]; s[i] = s[j]; s[j] = temp;
            out[n] = (byte) (data[n] ^ s[(s[i] + s[j]) & 255]);
        }
        return out;
    }
    private static byte[] inflate(byte[] input) {
        if (input.length < 8) throw invalid();
        ByteBuffer header = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt() != COMPRESSION_MAGIC) throw invalid();
        int length = header.getInt();
        if (length < 0 || length > MAX_BYTES) throw invalid();
        if (length == 0) { if (input.length != 8) throw invalid(); return new byte[0]; }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input, 8, input.length - 8);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && !inflater.finished()) throw invalid();
                if ((long) out.size() + count > length) throw invalid();
                out.write(buffer, 0, count);
            }
            if (out.size() != length || inflater.getRemaining() != 0) throw invalid();
            return out.toByteArray();
        } catch (DataFormatException e) { throw invalid(); }
        finally { inflater.end(); }
    }
    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(raw); deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            append(out, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(COMPRESSION_MAGIC).putInt(raw.length).array());
            if (raw.length == 0) return out.toByteArray();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if ((long) out.size() + count > MAX_BYTES) throw invalid();
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally { deflater.end(); }
    }
    private static List<byte[]> split(byte[] bytes, byte[] separator, int limit) {
        List<byte[]> result = new ArrayList<>(); int start = 0, next;
        while ((next = indexOf(bytes, separator, start)) >= 0) {
            if (result.size() >= limit - 1) throw invalid();
            result.add(Arrays.copyOfRange(bytes, start, next)); start = next + separator.length;
        }
        result.add(Arrays.copyOfRange(bytes, start, bytes.length)); return result;
    }
    private static int indexOf(byte[] data, byte[] pattern, int start) {
        outer: for (int i = start; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) if (data[i + j] != pattern[j]) continue outer;
            return i;
        }
        return -1;
    }
    private static void append(ByteArrayOutputStream out, byte[] data) {
        if ((long) out.size() + data.length > MAX_BYTES) throw invalid();
        out.write(data, 0, data.length);
    }
    private static byte[] join(byte[] a, byte[] b) { byte[] out = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, out, a.length, b.length); return out; }
    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
