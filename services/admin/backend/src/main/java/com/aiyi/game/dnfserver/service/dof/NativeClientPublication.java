package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.*;

/** Separates the private tool template from a credential-filtered validation candidate.
 * This class never publishes a file or asserts native-client acceptance.
 */
public final class NativeClientPublication {
    private NativeClientPublication() {}
    private static final List<String> CLIENT_FIELDS = Collections.unmodifiableList(Arrays.asList(
        "dffefrrr+(((((", "token_dof+", "token_dof-", "token_dof+((((("));
    private static final List<String> TOOL_FIELDS = Collections.unmodifiableList(Arrays.asList(
        "dffefrrr+(((((KLHGJLKEJGL", "dffefrrr+(((((KLHGJLKEJGL234", "dffefrrr+(((((KLHGJLKEJGL5"));
    private static final String PASSWORD_FIELD = TOOL_FIELDS.get(1);

    public static final class Candidate {
        private final byte[] bytes;
        private final String sha256;
        private final int removedRecords;
        private Candidate(byte[] bytes, int removedRecords) {
            this.bytes = bytes.clone(); this.sha256 = digest(bytes); this.removedRecords = removedRecords;
        }
        // Internal validation only. No HTTP handler is allowed to return this candidate directly.
        byte[] bytesForValidation() { return bytes.clone(); }
        public Map<String,Object> metadata() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("sha256", sha256); out.put("bytes",bytes.length); out.put("clientRecords",CLIENT_FIELDS.size());
            out.put("clientFormatVersion",NativeClientFormat.VERSION);
            out.put("removedToolRecords",removedRecords); out.put("credentialsFiltered",true);
            out.put("nativeClientAcceptance",false); out.put("publishable",false);
            return Collections.unmodifiableMap(out);
        }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("客户端发布模板含未知、重复、不完整或不安全记录；停止生成发布候选");
    }
    public static Candidate prepare(byte[] original, PublicKey boundGameIdentity) {
        NativeConfigContainer.Document source = NativeConfigContainer.decode(original,new byte[0]);
        if (!source.compressed || source.passwordRequired) throw invalid();
        Map<String,NativeConfigContainer.Entry> entries = new LinkedHashMap<>();
        int removed = 0;
        for (NativeConfigContainer.Entry e : source.entries) {
            String name = new String(e.name(),StandardCharsets.US_ASCII);
            if (!Arrays.equals(name.getBytes(StandardCharsets.US_ASCII),e.name())
                || (!CLIENT_FIELDS.contains(name) && !TOOL_FIELDS.contains(name) && !NativeClientFormat.VERSION_FIELD.equals(name)) || entries.put(name,e) != null) throw invalid();
            String expectedKind = CLIENT_FIELDS.get(0).equals(name) ? "PNG" : "TXT";
            if (!Arrays.equals(e.kind(),expectedKind.getBytes(StandardCharsets.US_ASCII))) throw invalid();
            if (TOOL_FIELDS.contains(name)) removed++;
        }
        if (!entries.keySet().containsAll(CLIENT_FIELDS) || (removed != 0 && removed != 3)) throw invalid();
        if (!Arrays.equals(entries.get("token_dof-").value(),NativeClientFormat.CONFIG_FILE.getBytes(StandardCharsets.US_ASCII))) throw invalid();
        validateBackground(entries.get(CLIENT_FIELDS.get(0)).value());
        NativeClientPayload.Payload payload = NativeClientPayload.decode(source,boundGameIdentity);
        byte[] password = entries.containsKey(PASSWORD_FIELD) ? entries.get(PASSWORD_FIELD).value() : new byte[0];
        List<NativeConfigContainer.Entry> kept = new ArrayList<>();
        for (NativeConfigContainer.Entry e : source.entries) {
            if (!CLIENT_FIELDS.contains(new String(e.name(),StandardCharsets.US_ASCII))) continue;
            if (password.length != 0 && (contains(e.value(),password) || payload.containsSensitiveValue(password))) throw invalid();
            kept.add(e);
        }
        NativeConfigContainer.Document clean = new NativeConfigContainer.Document(kept,true,false);
        byte[] generated = NativeConfigContainer.encode(clean,new byte[0]);
        NativeConfigContainer.Document checked = NativeConfigContainer.decode(generated,new byte[0]);
        if (!checked.equals(clean) || !NativeClientPayload.decode(checked,boundGameIdentity).endpoint().equals(payload.endpoint())) throw invalid();
        return new Candidate(generated,removed);
    }
    static NativeConfigContainer.Document withBackground(NativeConfigContainer.Document source, byte[] background) {
        validateBackground(background);
        List<NativeConfigContainer.Entry> changed = new ArrayList<>(); int count = 0;
        for (NativeConfigContainer.Entry entry : source.entries) {
            if (Arrays.equals(entry.name(), CLIENT_FIELDS.get(0).getBytes(StandardCharsets.US_ASCII))) {
                changed.add(new NativeConfigContainer.Entry(entry.name(), background, entry.kind())); count++;
            } else changed.add(entry);
        }
        if (count != 1) throw invalid();
        return new NativeConfigContainer.Document(changed, source.compressed, source.passwordRequired);
    }
    static void validateBackground(byte[] bytes) {
        if (bytes == null || bytes.length > 2 * 1024 * 1024) throw invalid();
        int width, height;
        if (bytes.length >= 13 && (starts(bytes,"GIF89a".getBytes(StandardCharsets.US_ASCII)) || starts(bytes,"GIF87a".getBytes(StandardCharsets.US_ASCII)))) {
            width = (bytes[6]&255) | ((bytes[7]&255)<<8); height = (bytes[8]&255) | ((bytes[9]&255)<<8);
        } else if (bytes.length >= 33 && starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10})
            && bytes[8]==0 && bytes[9]==0 && bytes[10]==0 && bytes[11]==13
            && starts(Arrays.copyOfRange(bytes,12,16),new byte[]{73,72,68,82})) {
            java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(bytes); width=header.getInt(16); height=header.getInt(20);
        } else throw invalid();
        if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long)width*height > 8388608) throw invalid();
        try {
            // The size gate precedes pixel decoding to bound allocations in headless Linux.
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != width || image.getHeight() != height) throw invalid();
            if (bytes[0] == (byte)137) {
                int offset = 8; boolean ended = false;
                while (offset <= bytes.length - 12) {
                    int length = java.nio.ByteBuffer.wrap(bytes, offset, 4).getInt();
                    if (length < 0 || length > bytes.length - offset - 12) throw invalid();
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(bytes, offset + 4, length + 4);
                    long expected = java.nio.ByteBuffer.wrap(bytes, offset + 8 + length, 4).getInt() & 0xffffffffL;
                    if (crc.getValue() != expected) throw invalid();
                    boolean last = bytes[offset+4] == 'I' && bytes[offset+5] == 'E' && bytes[offset+6] == 'N' && bytes[offset+7] == 'D';
                    offset += length + 12;
                    if (last) { if (length != 0 || offset != bytes.length) throw invalid(); ended = true; break; }
                }
                if (!ended) throw invalid();
            } else if (bytes[bytes.length - 1] != 0x3b) throw invalid();
        } catch (java.io.IOException e) { throw invalid(); }
    }
    private static boolean starts(byte[] value, byte[] prefix) { return value.length>=prefix.length && Arrays.equals(Arrays.copyOf(value,prefix.length),prefix); }
    private static boolean contains(byte[] bytes, byte[] pattern) {
        outer: for (int i=0;i<=bytes.length-pattern.length;i++) {
            for (int j=0;j<pattern.length;j++) if(bytes[i+j]!=pattern[j]) continue outer;
            return true;
        }
        return false;
    }
    private static String digest(byte[] bytes) {
        try { StringBuilder out=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString(); }
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
