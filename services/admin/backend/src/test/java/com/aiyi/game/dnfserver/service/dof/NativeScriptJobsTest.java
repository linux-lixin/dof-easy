package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class NativeScriptJobsTest {
    static final String REV=String.join("",Collections.nCopies(64,"a"));
    private static final ObjectMapper JSON=new ObjectMapper();
    static byte[] fixture(int seed) {
        byte[] raw=new byte[84];ByteBuffer h=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(36).put("11111111-2222-3333-4444-555555555555".getBytes(StandardCharsets.US_ASCII));
        h.putInt(0xf851).putInt(24).putInt(0x12345678).putInt(1);
        byte[] index=new byte[24];ByteBuffer b=ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(seed).putInt(1).put((byte)'x').putInt(4).putInt(0).putInt(0);
        // Original PVF encoding; zero-mask inverse produces ordinary index words.
        NativeScriptIndex.transform(index,0,index.length,0x12345678,0,false);
        h.put(index).putInt(seed);return raw;
    }
    private static class Fixture implements AutoCloseable {
        Path path=Files.createTempDirectory("dof_native-script-test-");
        Fixture()throws IOException{}
        NativeScriptJobs open()throws IOException{return new NativeScriptJobs(path,"isolated-test",p->Long.MAX_VALUE);}
        public void close()throws IOException{try(java.util.stream.Stream<Path>s=Files.walk(path)){for(Path p:(Iterable<Path>)s.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    static NativeScriptJobs.Request request(NativeScriptJobs store,byte[] source)throws IOException {
        NativeScriptJobs.Request r=new NativeScriptJobs.Request();r.requestId=UUID.randomUUID().toString();r.uploadId=UUID.randomUUID().toString();r.revision=REV;
        store.upload(r.uploadId,REV,42,source.length,new ByteArrayInputStream(source));return r;
    }
    static NativeScriptJobs.Job finished(NativeScriptJobs store,String id)throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        do{NativeScriptJobs.Job j=store.read(id);if(!Arrays.asList("RUNNING","QUEUED").contains(j.state))return j;Thread.sleep(20);}while(System.nanoTime()<until);throw new AssertionError("Worker timed out");
    }
    @Test public void pairedSourceCreatesIndependentValidatedCopyAndPreservesOriginal()throws Exception {
        try(Fixture f=new Fixture();NativeScriptJobs store=f.open()) {
            byte[] raw=fixture(19);Path source=f.path.resolve("paired.pvf");Files.write(source,raw);
            Files.setPosixFilePermissions(source,java.nio.file.attribute.PosixFilePermissions.fromString("r--------"));
            String id=UUID.randomUUID().toString();NativeScriptJobs.Upload result=store.uploadPaired(id,REV,42,source);
            assertEquals("READY",result.state);assertEquals(NativeScriptJobs.hash(source),result.sha256);
            Path copy=f.path.resolve("uploads").resolve(id).resolve("source.pvf");
            assertFalse(Files.isSameFile(copy,source));assertArrayEquals(raw,Files.readAllBytes(copy));
            assertArrayEquals(raw,Files.readAllBytes(source));
            try{store.uploadPaired(id,REV,42,source);fail();}catch(DofFailure e){assertEquals(409,e.status);}
        }
    }
    @Test public void sourceChangeDuringImportCannotBecomeReady()throws Exception {
        try(Fixture f=new Fixture()) {
            Path source=f.path.resolve("paired.pvf");Files.write(source,fixture(22));
            java.util.concurrent.atomic.AtomicBoolean changed=new java.util.concurrent.atomic.AtomicBoolean();
            try(NativeScriptJobs store=new NativeScriptJobs(f.path,"isolated-test",p->{
                if(changed.compareAndSet(false,true))Files.setLastModifiedTime(source,java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis()+5000));
                return Long.MAX_VALUE;
            })) {
                String id=UUID.randomUUID().toString();
                try{store.uploadPaired(id,REV,42,source);fail();}catch(IOException expected){}
                assertEquals("FAILED",store.readUpload(id).state);
                assertFalse(Files.exists(f.path.resolve("uploads").resolve(id).resolve("source.pvf")));
                assertFalse(Files.exists(f.path.resolve("uploads").resolve(id).resolve("source.pvf.part")));
            }
        }
    }
    @Test public void pairedSourceRejectsLinksMalformedDataAndInsufficientSpace()throws Exception {
        try(Fixture f=new Fixture();NativeScriptJobs store=f.open()) {
            Path source=f.path.resolve("paired.pvf"),link=f.path.resolve("link.pvf");Files.write(source,fixture(20));Files.createSymbolicLink(link,source);
            try{store.uploadPaired(UUID.randomUUID().toString(),REV,42,link);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            assertTrue(store.uploads().isEmpty());
            Files.write(source,new byte[84]);String id=UUID.randomUUID().toString();
            try{store.uploadPaired(id,REV,42,source);fail();}catch(IOException expected){}
            assertEquals("FAILED",store.readUpload(id).state);assertFalse(Files.exists(f.path.resolve("uploads").resolve(id).resolve("source.pvf")));
        }
        try(Fixture f=new Fixture();NativeScriptJobs store=new NativeScriptJobs(f.path,"isolated-test",p->NativeScriptJobs.RESERVE)) {
            Path source=f.path.resolve("paired.pvf");Files.write(source,fixture(21));
            try{store.uploadPaired(UUID.randomUUID().toString(),REV,42,source);fail();}catch(DofFailure e){assertEquals(507,e.status);}
            assertTrue(store.uploads().isEmpty());assertArrayEquals(fixture(21),Files.readAllBytes(source));
        }
    }
    @Test public void validatesOriginalIndexAndRejectsEncryptedOrTruncatedSources()throws Exception {
        byte[] original=fixture(4);assertEquals(1,NativePvfSource.validate(new ByteArrayInputStream(original),original.length).fileCount);
        byte[] encrypted=original.clone();NativeScriptIndex.transform(encrypted,56,24,0x12345678,1234,true);
        for(byte[] bad:new byte[][]{encrypted,Arrays.copyOf(original,original.length-1)}) {
            try{NativePvfSource.validate(new ByteArrayInputStream(bad),original.length);fail();}catch(IOException expected){}
        }
        byte[] corrupt=original.clone();ByteBuffer.wrap(corrupt).order(ByteOrder.LITTLE_ENDIAN).putInt(52,2);
        try{NativePvfSource.validate(new ByteArrayInputStream(corrupt),corrupt.length);fail();}catch(IOException expected){}
    }
    @Test public void uploadGenerateVerifyAndRestartPreserveBindingWithoutPublishingParameters()throws Exception {
        try(Fixture f=new Fixture()) {
            NativeScriptJobs.Job result;NativeScriptJobs.Request r;
            byte[] source=fixture(5);
            try(NativeScriptJobs store=f.open()) {
                r=request(store,source);store.submit(r,42);result=finished(store,r.requestId);
                assertEquals(result.error,"READY_FOR_TEST",result.state);assertEquals("NOT_APPLIED",result.applicationState);assertFalse(result.nativeClientAcceptance);
                assertTrue(result.roundTripVerified);
                NativeScriptJobs.Artifact a=store.artifact(r.requestId,REV);assertEquals(source.length+1280,Files.size(a.path));
                String envelope=store.parameters(r.requestId);assertFalse(JSON.writeValueAsString(result).contains(envelope));
                byte[] artifact=Files.readAllBytes(a.path);
                NativeScriptParameters parameters=NativeScriptParameters.decode(envelope,NativeScriptContext.CLIENT_DLL_SHA256);
                NativeScriptIndex.transform(artifact,56,24,0x12345678,parameters.effectiveMask(0x12345678),false);
                assertArrayEquals(source,Arrays.copyOf(artifact,source.length));
                assertEquals(result.sha256,store.submit(r,42).sha256);
                try{store.submit(r,43);fail();}catch(DofFailure e){assertEquals(409,e.status);}
                try{store.upload(r.uploadId,REV,42,source.length,new ByteArrayInputStream(source));fail();}catch(DofFailure e){assertEquals(409,e.status);}
            }
            try(NativeScriptJobs store=f.open()) {
                NativeScriptJobs.Job reloaded=store.artifact(r.requestId,REV).job;
                assertEquals(result.sha256,reloaded.sha256);assertTrue(reloaded.roundTripVerified);assertFalse(reloaded.nativeClientAcceptance);
            }
        }
    }
    @Test public void damagedArtifactsAndParametersCannotBeDownloaded()throws Exception {
        try(Fixture f=new Fixture();NativeScriptJobs store=f.open()) {
            NativeScriptJobs.Request r=request(store,fixture(7));store.submit(r,42);assertEquals("READY_FOR_TEST",finished(store,r.requestId).state);
            Path parameter=f.path.resolve("tasks").resolve(r.requestId).resolve("parameters.private");byte[] original=Files.readAllBytes(parameter);
            Files.write(parameter,NativeScriptParameters.create(new java.security.SecureRandom()).envelope(NativeScriptContext.CLIENT_DLL_SHA256).getBytes(StandardCharsets.US_ASCII));
            try{store.artifact(r.requestId,REV);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            Files.write(parameter,original);Path artifact=store.artifact(r.requestId,REV).path;Files.write(artifact,new byte[]{1});
            try{store.artifact(r.requestId,REV);fail();}catch(DofFailure e){assertEquals(409,e.status);}
        }
    }
    @Test public void uploadFailureLeavesInspectableRecordAndNoAcceptedSource()throws Exception {
        try(Fixture f=new Fixture();NativeScriptJobs store=f.open()) {
            String id=UUID.randomUUID().toString();byte[] source=fixture(1);
            try{store.upload(id,REV,42,source.length+1,new ByteArrayInputStream(source));fail();}catch(IOException expected){}
            assertEquals("FAILED",store.readUpload(id).state);assertFalse(Files.exists(f.path.resolve("uploads").resolve(id).resolve("source.pvf")));
            assertFalse(Files.exists(f.path.resolve("uploads").resolve(id).resolve("source.pvf.part")));
        }
    }
    @Test public void capacityInstanceAndConcurrentWorkerGuards()throws Exception {
        try(Fixture f=new Fixture();NativeScriptJobs store=new NativeScriptJobs(f.path,"isolated-test",p->NativeScriptJobs.RESERVE)) {
            try{request(store,fixture(1));fail();}catch(DofFailure e){assertEquals(507,e.status);}
            assertTrue(store.uploads().isEmpty());
            try(NativeScriptJobs other=f.open()){fail();}catch(DofFailure e){assertEquals(409,e.status);}
            try{store.readUpload("../tasks");fail();}catch(DofFailure e){assertEquals(400,e.status);}
        }
    }
    @Test public void staleUploadAndInterruptedWorkerAreNotReplayedAfterRestart()throws Exception {
        try(Fixture f=new Fixture()) {
            NativeScriptJobs.Request r;NativeScriptJobs.Job j;
            try(NativeScriptJobs store=f.open()) {
                r=request(store,fixture(1));store.submit(r,42);j=finished(store,r.requestId);
            }
            j.state="RUNNING";JSON.writeValue(f.path.resolve("tasks").resolve(r.requestId).resolve("record.json").toFile(),j);
            try(NativeScriptJobs store=f.open()) {
                assertEquals("INTERRUPTED",store.read(r.requestId).state);
                assertEquals("INTERRUPTED",store.submit(r,42).state);
                try{store.artifact(r.requestId,REV);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            }
        }
    }
    @Test public void currentPairedSourceStructureWhenProvided()throws Exception {
        String path=System.getProperty("dof.script.pairDirectory");Assume.assumeNotNull(path);
        Path p=Paths.get(path).resolve("Script.pvf");
        try(InputStream in=Files.newInputStream(p)){NativePvfSource.Header h=NativePvfSource.validate(in,Files.size(p));assertEquals(894478,h.fileCount);}
    }
    @Test public void originalProducerVectorsMatchNewParameterAndWholeFileCandidate()throws Exception {
        String path=System.getProperty("dof.script.uncompressedVectors");Assume.assumeNotNull(path);
        JsonNode vectors=JSON.readTree(new File(path)).get("vectors");assertEquals(3,vectors.size());
        for(JsonNode v:vectors) {
            byte[] source=decode(v.get("sourceHex").asText()),expected=decode(v.get("nativeHex").asText());
            String envelope=NativeScriptContext.encode(v.get("payload").asText().getBytes(StandardCharsets.US_ASCII),NativeScriptContext.CLIENT_DLL_SHA256);
            NativeScriptParameters p=NativeScriptParameters.decode(envelope,NativeScriptContext.CLIENT_DLL_SHA256);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            NativeScriptFileCodec.encode(new ByteArrayInputStream(source),out,source.length,v.get("sourceSha256").asText(),
                p.effectiveMask(ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(48)),false,Arrays.copyOfRange(expected,expected.length-1280,expected.length));
            assertArrayEquals(expected,out.toByteArray());
        }
    }
    private static byte[] decode(String s){byte[] out=new byte[s.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return out;}
}
