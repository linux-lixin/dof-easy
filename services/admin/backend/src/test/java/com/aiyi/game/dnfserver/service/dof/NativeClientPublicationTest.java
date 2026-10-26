package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static org.junit.Assert.*;

public class NativeClientPublicationTest {
    private static byte[] b(String s){return s.getBytes(StandardCharsets.US_ASCII);}
    private static NativeConfigContainer.Entry e(String n,String v){return new NativeConfigContainer.Entry(b(n),b(v),b("TXT"));}
    static NativeConfigContainer.Document fixture(KeyPair client) throws Exception {
        List<NativeConfigContainer.Entry> entries=new ArrayList<>(NativeClientPayloadTest.fixture(client).entries);
        entries.remove(0);
        java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB), "PNG", image);
        entries.add(0,new NativeConfigContainer.Entry(b("dffefrrr+((((("),image.toByteArray(),b("PNG")));
        entries.add(2,e("token_dof-","dof-client.dll"));
        entries.add(e("dffefrrr+(((((KLHGJLKEJGL","db.internal"));
        entries.add(e("dffefrrr+(((((KLHGJLKEJGL234","TEST-DB-SECRET-NEVER-PUBLISH"));
        entries.add(e("dffefrrr+(((((KLHGJLKEJGL5","root"));
        return new NativeConfigContainer.Document(entries,true,false);
    }
    private static byte[] wire(List<NativeConfigContainer.Entry> entries){return NativeConfigContainer.encode(new NativeConfigContainer.Document(entries,true,false),new byte[0]);}
    private static void rejected(Runnable r){try{r.run();fail("Unsafe candidate accepted");}catch(IllegalArgumentException expected){}}
    @Test public void removesOnlyToolRecordsAndKeepsSourceImmutable() throws Exception {
        KeyPair client=NativeClientPayloadTest.pair(),server=NativeClientPayloadTest.pair();
        NativeConfigContainer.Document source=fixture(client);byte[] original=NativeConfigContainer.encode(source,new byte[0]);byte[] before=original.clone();
        NativeClientPublication.Candidate candidate=NativeClientPublication.prepare(original,server.getPublic());
        NativeConfigContainer.Document clean=NativeConfigContainer.decode(candidate.bytesForValidation(),new byte[0]);
        assertEquals(4,clean.entries.size());assertArrayEquals(before,original);
        for(int i=0;i<4;i++)assertEquals(source.entries.get(i),clean.entries.get(i));
        assertEquals(3,candidate.metadata().get("removedToolRecords"));assertEquals(false,candidate.metadata().get("publishable"));
        assertFalse(candidate.metadata().toString().contains("TEST-DB-SECRET"));
        NativeClientPublication.Candidate again=NativeClientPublication.prepare(candidate.bytesForValidation(),server.getPublic());
        assertArrayEquals(candidate.bytesForValidation(),again.bytesForValidation());
    }
    @Test public void rejectsUnknownDuplicatePartialCredentialsAndGameIdentity() throws Exception {
        KeyPair client=NativeClientPayloadTest.pair(),server=NativeClientPayloadTest.pair();NativeConfigContainer.Document source=fixture(client);
        List<NativeConfigContainer.Entry> unknown=new ArrayList<>(source.entries);unknown.add(e("unreviewed","possible secret"));
        rejected(()->NativeClientPublication.prepare(wire(unknown),server.getPublic()));
        List<NativeConfigContainer.Entry> duplicate=new ArrayList<>(source.entries);duplicate.add(source.entries.get(4));
        rejected(()->NativeClientPublication.prepare(wire(duplicate),server.getPublic()));
        List<NativeConfigContainer.Entry> partial=new ArrayList<>(source.entries);partial.remove(6);
        rejected(()->NativeClientPublication.prepare(wire(partial),server.getPublic()));
        rejected(()->NativeClientPublication.prepare(wire(source.entries),client.getPublic()));
    }
    @Test public void rejectsCredentialsCopiedIntoEncryptedPayload() throws Exception {
        KeyPair client=NativeClientPayloadTest.pair(),server=NativeClientPayloadTest.pair();List<NativeConfigContainer.Entry> source=new ArrayList<>(fixture(client).entries);
        source.set(6,e("dffefrrr+(((((KLHGJLKEJGL234","opaque-1"));
        rejected(()->NativeClientPublication.prepare(wire(source),server.getPublic()));
    }
    @Test public void rejectsTruncatedCorruptedAndAppendedBackgroundData() throws Exception {
        KeyPair client=NativeClientPayloadTest.pair(),server=NativeClientPayloadTest.pair();
        List<NativeConfigContainer.Entry> source=new ArrayList<>(fixture(client).entries);
        byte[] image=source.get(0).value();
        byte[] truncated=Arrays.copyOf(image,33), corrupted=image.clone(), appended=Arrays.copyOf(image,image.length+1);
        corrupted[29]^=1;
        for(byte[] bad:Arrays.asList(truncated,corrupted,appended)) {
            source.set(0,new NativeConfigContainer.Entry(b("dffefrrr+((((("),bad,b("PNG")));
            rejected(()->NativeClientPublication.prepare(wire(source),server.getPublic()));
        }
    }
    @Test public void actualTemplateKeepsFiveVersionedClientRecords() throws Exception {
        String path=System.getProperty("dof.native.current",""),identity=System.getProperty("dof.native.public","");
        org.junit.Assume.assumeFalse("Private fixtures absent",path.isEmpty()||identity.isEmpty());
        byte[] original=Files.readAllBytes(Paths.get(path));
        PublicKey server=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Paths.get(identity))));
        NativeClientPublication.Candidate candidate=NativeClientPublication.prepare(original,server);
        NativeConfigContainer.Document originalDoc=NativeConfigContainer.decode(original,new byte[0]),clean=NativeConfigContainer.decode(candidate.bytesForValidation(),new byte[0]);
        assertEquals(4,clean.entries.size());for(int i=0;i<4;i++)assertEquals(originalDoc.entries.get(i),clean.entries.get(i));
        assertEquals(NativeClientPayload.decode(originalDoc,server).endpoint(),NativeClientPayload.decode(clean,server).endpoint());
        assertEquals(originalDoc.entries.size()-5,candidate.metadata().get("removedToolRecords"));
    }
}
