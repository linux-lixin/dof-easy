package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.crypto.Cipher;
import static org.junit.Assert.*;

public class NativeClientPayloadTest {
    private static byte[] b(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    static KeyPair pair() throws Exception { KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); return g.generateKeyPair(); }
    static NativeConfigContainer.Document fixture(KeyPair client) throws Exception {
        byte[] pkcs1 = PrivateKeyInfo.getInstance(client.getPrivate().getEncoded()).parsePrivateKey().toASN1Primitive().getEncoded();
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + Base64.getMimeEncoder(64,b("\n")).encodeToString(pkcs1) + "\n-----END RSA PRIVATE KEY-----\n";
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding"); c.init(Cipher.ENCRYPT_MODE,client.getPublic());
        byte[] data = c.doFinal(b("127.0.0.1*&dof-client.dll*&opaque-1*&opaque-2*&opaque-3*&opaque-4*&opaque-5*&opaque-6*&"));
        return new NativeConfigContainer.Document(Arrays.asList(
            new NativeConfigContainer.Entry(b("unknown"),new byte[]{0,1,2},b("BINARY")),
            new NativeConfigContainer.Entry(b("token_dof+"), Base64.getEncoder().encode(b(pem)), b("TXT")),
            new NativeConfigContainer.Entry(b("token_dof+((((("),Base64.getEncoder().encode(data),b("TXT")),
            new NativeConfigContainer.Entry(b("token_dof_version"),b("2"),b("TXT"))),true,false);
    }
    private static void rejects(Runnable action) { try { action.run(); fail("Must reject invalid native payload"); } catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().contains("PRIVATE KEY")); } }
    @Test public void endpointChangesPreserveClientIdentityAndUnknownRecords() throws Exception {
        KeyPair client = pair(), server = pair(); NativeConfigContainer.Document doc = fixture(client);
        assertEquals("127.0.0.1",NativeClientPayload.decode(doc,server.getPublic()).endpoint());
        NativeConfigContainer.Document changed = NativeClientPayload.withEndpoint(doc,server.getPublic(),"192.0.2.10");
        assertEquals("192.0.2.10",NativeClientPayload.decode(changed,server.getPublic()).endpoint());
        assertEquals(doc.entries.get(0),changed.entries.get(0)); assertEquals(doc.entries.get(1),changed.entries.get(1));
        assertEquals(8,NativeClientPayload.decode(changed,server.getPublic()).fieldCount());
    }
    @Test public void serverIdentityCannotBecomeDownloadKeyAndDelimitersCannotBeInjected() throws Exception {
        KeyPair client = pair(), server = pair(); NativeConfigContainer.Document doc = fixture(client);
        rejects(() -> NativeClientPayload.decode(doc,client.getPublic()));
        rejects(() -> NativeClientPayload.withEndpoint(doc,server.getPublic(),"host*&injected"));
        rejects(() -> NativeClientPayload.withEndpoint(doc,server.getPublic(),"host/path"));
        for (String bad : new String[]{"", "127.0.0.999", "127.00.0.1", "host..invalid", "-bad.test", "bad-.test"})
            rejects(() -> NativeClientPayload.withEndpoint(doc,server.getPublic(),bad));
        rejects(() -> NativeClientPayload.decode(doc,null));
        List<NativeConfigContainer.Entry> duplicate = new ArrayList<>(doc.entries); duplicate.add(doc.entries.get(1));
        rejects(() -> NativeClientPayload.decode(new NativeConfigContainer.Document(duplicate,true,false),server.getPublic()));
    }
    @Test public void formatVersionMustBePresentUniqueAndCurrent() throws Exception {
        KeyPair server=pair();NativeConfigContainer.Document source=fixture(pair());
        List<NativeConfigContainer.Entry> missing=new ArrayList<>(source.entries);missing.remove(missing.size()-1);
        rejects(()->NativeClientPayload.decode(new NativeConfigContainer.Document(missing,true,false),server.getPublic()));
        List<NativeConfigContainer.Entry> wrong=new ArrayList<>(missing);
        wrong.add(new NativeConfigContainer.Entry(b("token_dof_version"),b("1"),b("TXT")));
        rejects(()->NativeClientPayload.decode(new NativeConfigContainer.Document(wrong,true,false),server.getPublic()));
        List<NativeConfigContainer.Entry> duplicate=new ArrayList<>(source.entries);duplicate.add(source.entries.get(source.entries.size()-1));
        rejects(()->NativeClientPayload.decode(new NativeConfigContainer.Document(duplicate,true,false),server.getPublic()));
    }
    @Test public void privateNativeFixtureCanBeReencodedWithoutChangingExistingKey() throws Exception {
        String fixture = System.getProperty("dof.native.current", ""), identity = System.getProperty("dof.native.public", "");
        org.junit.Assume.assumeFalse("Private native fixture/public identity not configured",fixture.isEmpty() || identity.isEmpty());
        PublicKey server = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Paths.get(identity))));
        NativeConfigContainer.Document doc = NativeConfigContainer.decode(Files.readAllBytes(Paths.get(fixture)),new byte[0]);
        String originalEndpoint=NativeClientPayload.decode(doc,server).endpoint();
        NativeConfigContainer.Document changed = NativeClientPayload.withEndpoint(doc,server,"192.0.2.10");
        assertEquals("192.0.2.10",NativeClientPayload.decode(changed,server).endpoint());
        for (int i = 0; i < doc.entries.size(); i++) if (i != 3) assertEquals(doc.entries.get(i),changed.entries.get(i));
        NativeConfigContainer.Document restored = NativeClientPayload.withEndpoint(changed,server,originalEndpoint);
        assertEquals(originalEndpoint,NativeClientPayload.decode(restored,server).endpoint());
        // Randomized PKCS#1 padding changes bytes; unchanged semantic fields/keys are the contract.
        assertEquals(restored,NativeConfigContainer.decode(NativeConfigContainer.encode(restored,new byte[0]),new byte[0]));
    }
}
