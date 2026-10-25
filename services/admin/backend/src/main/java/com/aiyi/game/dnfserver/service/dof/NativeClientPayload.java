package com.aiyi.game.dnfserver.service.dof;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.Cipher;

/** Offline inner-payload adapter. No key generation, database writes or package publication. */
public final class NativeClientPayload {
    private static final byte[] KEY_NAME = ascii("token_qingkong+");
    private static final byte[] PAYLOAD_NAME = ascii("token_qingkong+(((((");
    private static final int MAX_KEY_BYTES = 4096;
    private NativeClientPayload() {}

    public static final class Payload {
        private final List<String> fields;
        private Payload(List<String> fields) { this.fields = Collections.unmodifiableList(new ArrayList<>(fields)); }
        public String endpoint() { return fields.get(0); }
        public int fieldCount() { return fields.size(); }
        boolean containsSensitiveValue(byte[] value) {
            byte[] plain=wire();
            outer: for(int i=0;i<=plain.length-value.length;i++) {
                for(int j=0;j<value.length;j++) if(plain[i+j]!=value[j]) continue outer;
                return true;
            }
            return false;
        }
        // Unknown fields remain private and unchanged. Do not make this object a JSON DTO.
        private byte[] wire() { return ascii(String.join("*&", fields) + "*&"); }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("晴空客户端内层配置或密钥不兼容；未生成下载包");
    }
    private static NativeConfigContainer.Entry unique(NativeConfigContainer.Document doc, byte[] name) {
        NativeConfigContainer.Entry found = null;
        for (NativeConfigContainer.Entry entry : doc.entries) if (Arrays.equals(entry.name(), name)) {
            if (found != null || !Arrays.equals(entry.kind(), ascii("TXT"))) throw invalid();
            found = entry;
        }
        if (found == null) throw invalid();
        return found;
    }
    private static RSAPrivateCrtKey privateKey(NativeConfigContainer.Document doc) throws Exception {
        byte[] encoded = unique(doc, KEY_NAME).value();
        if (encoded.length > MAX_KEY_BYTES) throw invalid();
        byte[] pem = Base64.getDecoder().decode(encoded);
        for (byte c : pem) if (c < 0) throw invalid();
        try (PEMParser parser = new PEMParser(new StringReader(new String(pem, StandardCharsets.US_ASCII)))) {
            Object parsed = parser.readObject();
            if (!(parsed instanceof PEMKeyPair) || parser.readObject() != null) throw invalid();
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(((PEMKeyPair) parsed).getPrivateKeyInfo().getEncoded()));
            if (!(key instanceof RSAPrivateCrtKey) || ((RSAPrivateCrtKey) key).getModulus().bitLength() != 2048) throw invalid();
            return (RSAPrivateCrtKey) key;
        }
    }
    /** Requires the bound game-server public key; the server private key is never an input. */
    private static RSAPrivateCrtKey checkedKey(NativeConfigContainer.Document doc, PublicKey serverIdentity) throws Exception {
        if (!(serverIdentity instanceof RSAPublicKey)) throw invalid();
        RSAPrivateCrtKey key = privateKey(doc);
        RSAPublicKey server = (RSAPublicKey) serverIdentity;
        if (key.getModulus().equals(server.getModulus())) throw invalid();
        return key;
    }
    public static Payload decode(NativeConfigContainer.Document doc, PublicKey serverIdentity) {
        try {
            RSAPrivateCrtKey key = checkedKey(doc, serverIdentity);
            byte[] encoded = unique(doc, PAYLOAD_NAME).value();
            if (encoded.length != 344) throw invalid();
            byte[] encrypted = Base64.getDecoder().decode(encoded);
            if (encrypted.length != 256) throw invalid();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] raw = cipher.doFinal(encrypted);
            for (byte c : raw) if (c < 32 || c > 126) throw invalid();
            String[] fields = new String(raw, StandardCharsets.US_ASCII).split("\\*&", -1);
            if (fields.length != 9 || !fields[8].isEmpty() || !"qingkong.dll".equals(fields[1])) throw invalid();
            return new Payload(Arrays.asList(Arrays.copyOf(fields, 8)));
        } catch (Exception e) { throw invalid(); }
    }
    /** Re-encrypts only the endpoint while retaining the existing client key and all other records.
     * The returned document remains an internal artifact; it may still contain tool DB credentials.
     */
    public static NativeConfigContainer.Document withEndpoint(NativeConfigContainer.Document doc, PublicKey serverIdentity, String endpoint) {
        if (!validEndpoint(endpoint)) throw invalid();
        try {
            Payload original = decode(doc, serverIdentity);
            RSAPrivateCrtKey privateKey = checkedKey(doc, serverIdentity);
            List<String> fields = new ArrayList<>(original.fields); fields.set(0, endpoint);
            byte[] raw = new Payload(fields).wire(); if (raw.length > 245) throw invalid();
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = Base64.getEncoder().encode(cipher.doFinal(raw));
            List<NativeConfigContainer.Entry> entries = new ArrayList<>();
            for (NativeConfigContainer.Entry entry : doc.entries) entries.add(Arrays.equals(entry.name(), PAYLOAD_NAME)
                ? new NativeConfigContainer.Entry(entry.name(), encrypted, entry.kind()) : entry);
            NativeConfigContainer.Document result = new NativeConfigContainer.Document(entries, doc.compressed, doc.passwordRequired);
            Payload verified = decode(result, serverIdentity);
            if (!verified.fields.equals(fields)) throw invalid();
            return result;
        } catch (Exception e) { throw invalid(); }
    }
    private static boolean validEndpoint(String value) {
        if (value == null || value.isEmpty() || value.length() > 253) return false;
        String[] labels = value.split("\\.", -1);
        for (String label : labels) if (label.length() > 63 || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")) return false;
        if (value.matches("[0-9.]+")) {
            if (labels.length != 4) return false;
            for (String label : labels) {
                if (label.length() > 3 || (label.length() > 1 && label.startsWith("0")) || Integer.parseInt(label) > 255) return false;
            }
        }
        return true;
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
