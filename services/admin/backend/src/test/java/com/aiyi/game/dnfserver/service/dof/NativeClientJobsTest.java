package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NativeClientJobsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSTANCE = "dof-contract-only";
    private static final class Fixture implements AutoCloseable {
        final Path root = Files.createTempDirectory("dof_native-publication-test-");
        final KeyPair identity = NativeClientPayloadTest.pair();
        Fixture() throws Exception {
            Path inputs = Files.createDirectory(root.resolve("inputs"));
            Map<String,byte[]> files = new LinkedHashMap<>();
            files.put("template.dll", NativeConfigContainer.encode(NativeClientPublicationTest.fixture(NativeClientPayloadTest.pair()), new byte[0]));
            files.put("game-public.der", identity.getPublic().getEncoded());
            files.put("dof-launcher.exe", "fixture-launcher".getBytes(StandardCharsets.US_ASCII));
            files.put("ijl15.dll", "fixture-client-library".getBytes(StandardCharsets.US_ASCII));
            NativeClientJobs.Binding binding = new NativeClientJobs.Binding(); binding.instanceId=INSTANCE; binding.environment="isolated";
            binding.adapterVersion=DofAdminService.VERSION;binding.clientFormatVersion=NativeClientFormat.VERSION; binding.sha256=new LinkedHashMap<>();
            binding.databaseUuid="00000000-0000-4000-8000-000000000001";binding.readerRecipe="dof-launcher-reader-2.2";
            for(Map.Entry<String,byte[]> file:files.entrySet()) { Files.write(inputs.resolve(file.getKey()),file.getValue()); binding.sha256.put(file.getKey(),NativeUiState.sha256(file.getValue())); }
            JSON.writeValue(inputs.resolve("binding.json").toFile(),binding);
            // Must never be traversed or included, despite being next to allowed inputs.
            Files.write(inputs.resolve("server-private.pem"), "FORBIDDEN-SERVER-PRIVATE-KEY".getBytes(StandardCharsets.US_ASCII));
        }
        NativeClientJobs open() throws IOException { return new NativeClientJobs(root,INSTANCE); }
        NativeClientJobs open(NativeClientJobs.SpaceProbe probe) throws IOException { return new NativeClientJobs(root,INSTANCE,probe); }
        public void close() throws IOException { try(java.util.stream.Stream<Path> paths=Files.walk(root)) { for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p); } }
    }
    private static void saveHistoricalJob(Path path, NativeClientJobs.Job job) throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode wire = JSON.valueToTree(job);
        wire.remove(Arrays.asList("manifestVersion","sourceBinding","manifestSha256","scriptBatch"));
        JSON.writeValue(path.toFile(),wire);
    }
    private static void saveProvenance(Path path, NativeClientJobs.Job job) throws IOException {
        Map<String,Object> wire = new LinkedHashMap<>();
        wire.put("manifestVersion",job.manifestVersion); wire.put("sourceBinding",job.sourceBinding);
        wire.put("manifestSha256",job.manifestSha256); wire.put("scriptBatch",job.scriptBatch);
        JSON.writeValue(path.resolveSibling("provenance.json").toFile(),wire);
    }
    private static NativeClientJobs.Request request(NativeClientJobs jobs) throws IOException {
        NativeClientJobs.Request request=new NativeClientJobs.Request(); request.requestId=UUID.randomUUID().toString();
        request.endpoint="192.0.2.44"; request.revision=(String)jobs.status().get("revision"); return request;
    }
    @Test public void sameInstanceNameCannotReuseInputsFromAnotherDatabase() throws Exception {
        try(Fixture f=new Fixture();NativeClientJobs jobs=f.open()) {
            jobs.requireDatabase("00000000-0000-4000-8000-000000000001");
            try { jobs.requireDatabase("00000000-0000-4000-8000-000000000002"); fail(); }
            catch(DofFailure e) { assertEquals(409,e.status); }
            try { jobs.requireDatabase(null); fail(); }
            catch(DofFailure e) { assertEquals(409,e.status); }
        }
    }
    private static NativeClientJobs.Job finished(NativeClientJobs jobs, String id) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        do { NativeClientJobs.Job result=jobs.read(id); if(!Arrays.asList("QUEUED","RUNNING").contains(result.state)) return result; Thread.sleep(25); }
        while(System.nanoTime()<end); throw new AssertionError("Task did not finish");
    }
    @Test public void packageContainsOnlyFilteredAssetsAndSurvivesRestart() throws Exception {
        try(Fixture f=new Fixture()) {
            String id,hash;
            try(NativeClientJobs jobs=f.open()) {
                NativeClientJobs.Request request=request(jobs); id=request.requestId;
                byte[] before=Files.readAllBytes(f.root.resolve("inputs/template.dll")); jobs.submit(request,42);
                NativeClientJobs.Job job=finished(jobs,id); assertEquals("READY_FOR_TEST",job.state); assertFalse(job.nativeClientAcceptance); hash=job.sha256;
                assertEquals(hash,jobs.submit(request,42).sha256);
                assertArrayEquals(before,Files.readAllBytes(f.root.resolve("inputs/template.dll")));
                Set<String> names=new HashSet<>();
                try(ZipFile zip=new ZipFile(jobs.download(id).toFile())) {
                    Enumeration<? extends ZipEntry> entries=zip.entries();
                    while(entries.hasMoreElements()) {
                        ZipEntry entry=entries.nextElement(); names.add(entry.getName());
                        ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream in=zip.getInputStream(entry)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)data.write(b,0,n);}
                        String plain=new String(data.toByteArray(),StandardCharsets.ISO_8859_1);
                        assertFalse(plain.contains("FORBIDDEN-SERVER-PRIVATE-KEY")); assertFalse(plain.contains("TEST-DB-SECRET-NEVER-PUBLISH"));
                        if(entry.getName().equals("dof-client.dll")) {
                            NativeConfigContainer.Document config=NativeConfigContainer.decode(data.toByteArray(),new byte[0]); assertEquals(4,config.entries.size());
                            assertEquals(request.endpoint,NativeClientPayload.decode(config,f.identity.getPublic()).endpoint());
                        }
                    }
                }
                assertEquals(new HashSet<>(Arrays.asList("dof-launcher.exe","ijl15.dll","dof-client.dll","manifest.json","使用与恢复.txt")),names);
                request.endpoint="192.0.2.45";
                try{jobs.submit(request,42);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            }
            try(NativeClientJobs restarted=f.open()) { assertEquals(hash,restarted.read(id).sha256);assertTrue(Files.exists(restarted.download(id))); }
        }
    }
    @Test public void manifestPinsDatabaseReaderMaterialsAndTaskAcrossRestart() throws Exception {
        try (Fixture f = new Fixture()) {
            String id;
            try (NativeClientJobs jobs = f.open()) {
                NativeClientJobs.Request request = request(jobs); id = request.requestId;
                jobs.submit(request,42); NativeClientJobs.Job job = finished(jobs,id);
                assertEquals("READY_FOR_TEST",job.state);
                assertEquals(1,job.manifestVersion);
                Map<?,?> historical = JSON.readValue(f.root.resolve("jobs").resolve(id).resolve("job.json").toFile(),Map.class);
                for (String field : Arrays.asList("manifestVersion","sourceBinding","manifestSha256","scriptBatch"))
                    assertFalse("old readers must not see new fields: " + field,historical.containsKey(field));
                try (ZipFile zip = new ZipFile(jobs.download(id).toFile());
                     InputStream in = zip.getInputStream(zip.getEntry("manifest.json"))) {
                    byte[] bytes = new byte[(int)zip.getEntry("manifest.json").getSize()];
                    new DataInputStream(in).readFully(bytes);
                    assertEquals(NativeUiState.sha256(bytes),job.manifestSha256);
                    Map<?,?> manifest = JSON.readValue(bytes,Map.class);
                    assertEquals(id,manifest.get("requestId"));
                    assertEquals(job.sourceBinding.databaseUuid,manifest.get("databaseUuid"));
                    assertEquals(job.sourceBinding.readerRecipe,manifest.get("readerRecipe"));
                    assertEquals(job.sourceBinding.sha256,manifest.get("sourceSha256"));
                    assertEquals(request.endpoint,manifest.get("endpoint"));
                    assertEquals("bound-material-hashes",manifest.get("sourceVerification"));
                    assertEquals(Boolean.FALSE,manifest.get("nativeClientAcceptance"));
                }
            }
            try (NativeClientJobs jobs = f.open()) {
                assertTrue(Files.exists(jobs.download(id)));
                NativeClientJobs.Job job = jobs.read(id);
                job.sourceBinding.databaseUuid = "00000000-0000-4000-8000-000000000002";
                Path record = f.root.resolve("jobs").resolve(id).resolve("job.json");
                saveProvenance(record,job);
                try { jobs.download(id); fail(); } catch (DofFailure e) { assertEquals(409,e.status); }
            }
        }
    }
    @Test public void alteredManifestReceiptAndMixedLegacyMetadataAreRejected() throws Exception {
        try (Fixture f = new Fixture(); NativeClientJobs jobs = f.open()) {
            NativeClientJobs.Request request = request(jobs); jobs.submit(request,42);
            NativeClientJobs.Job job = finished(jobs,request.requestId);
            assertEquals("READY_FOR_TEST",job.state);
            Path record = f.root.resolve("jobs").resolve(job.requestId).resolve("job.json");
            job.manifestSha256 = String.join("",Collections.nCopies(64,"0"));
            saveProvenance(record,job);
            try { jobs.download(job.requestId); fail(); } catch (DofFailure e) { assertEquals(409,e.status); }
            job.manifestVersion = 0;
            saveProvenance(record,job);
            try { jobs.download(job.requestId); fail(); } catch (DofFailure e) { assertEquals(409,e.status); }
            // Pre-provenance records remain readable/downloadable under the existing archive hash,
            // but never acquire the new version or claim newly verified provenance.
            job.manifestSha256 = null; job.sourceBinding = null;
            Files.delete(record.resolveSibling("provenance.json"));
            assertTrue(Files.exists(jobs.download(job.requestId)));
            assertEquals(0,jobs.read(job.requestId).manifestVersion);
            assertNull(jobs.submit(request,42).sourceBinding);
        }
    }
    @Test public void changedInputsFailWithoutDownloadOrRetryingOldRequest() throws Exception {
        try(Fixture f=new Fixture();NativeClientJobs jobs=f.open()) {
            NativeClientJobs.Request request=request(jobs);
            Files.write(f.root.resolve("inputs/ijl15.dll"),new byte[]{1});jobs.submit(request,42);
            assertEquals("FAILED",finished(jobs,request.requestId).state);
            assertEquals("FAILED",jobs.submit(request,42).state);
            try{jobs.download(request.requestId);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            assertFalse(Files.exists(f.root.resolve("jobs").resolve(request.requestId).resolve("candidate.zip")));
        }
    }
    @Test public void oldFormatJobCannotBeDownloadedOrReused() throws Exception {
        try(Fixture f=new Fixture();NativeClientJobs jobs=f.open()) {
            NativeClientJobs.Request request=request(jobs);jobs.submit(request,42);
            NativeClientJobs.Job job=finished(jobs,request.requestId);assertEquals("READY_FOR_TEST",job.state);
            job.clientFormatVersion=0;
            saveHistoricalJob(f.root.resolve("jobs").resolve(job.requestId).resolve("job.json"),job);
            try{jobs.download(job.requestId);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            try{jobs.submit(request,42);fail();}catch(DofFailure e){assertEquals(409,e.status);}
        }
    }
    @Test public void corruptPackageIsRejectedAndInterruptedStateIsRecovered() throws Exception {
        try(Fixture f=new Fixture()) {
            NativeClientJobs.Job job;
            try(NativeClientJobs jobs=f.open()) {
                NativeClientJobs.Request request=request(jobs);jobs.submit(request,42);job=finished(jobs,request.requestId);
                Files.write(jobs.download(job.requestId),new byte[]{1,2,3});
                try{jobs.download(job.requestId);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            }
            job.state="RUNNING";saveHistoricalJob(f.root.resolve("jobs").resolve(job.requestId).resolve("job.json"),job);
            try(NativeClientJobs jobs=f.open()) { assertEquals("INTERRUPTED",jobs.read(job.requestId).state);try{jobs.download(job.requestId);fail();}catch(DofFailure e){assertEquals(409,e.status);} }
        }
    }
    @Test public void wrongInstanceConcurrentWorkerAndSymlinkInputsAreRejected() throws Exception {
        try(Fixture f=new Fixture();NativeClientJobs jobs=f.open()) {
            try(NativeClientJobs unexpected=f.open()){fail();}catch(DofFailure e){assertEquals(409,e.status);}
            NativeClientJobs.Request request=request(jobs);Path source=f.root.resolve("inputs/ijl15.dll");Files.delete(source);Files.createSymbolicLink(source,f.root.resolve("inputs/server-private.pem"));
            jobs.submit(request,42);assertEquals("FAILED",finished(jobs,request.requestId).state);
            try{jobs.read("../inputs/binding.json");fail();}catch(DofFailure e){assertEquals(400,e.status);}
        }
        try(Fixture f=new Fixture()) {
            try(NativeClientJobs unexpected=new NativeClientJobs(f.root,"another-instance")){fail();}catch(DofFailure e){assertEquals(409,e.status);}
        }
    }
    @Test public void backgroundReplacementIsAuditedAndCannotBeChangedOnReplay() throws Exception {
        try(Fixture f=new Fixture();NativeClientJobs jobs=f.open()) {
            ByteArrayOutputStream image=new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"PNG",image);
            NativeClientJobs.Request request=request(jobs); request.backgroundBase64=Base64.getEncoder().encodeToString(image.toByteArray());
            jobs.submit(request,42); NativeClientJobs.Job job=finished(jobs,request.requestId);
            assertEquals("READY_FOR_TEST",job.state); assertEquals(NativeUiState.sha256(image.toByteArray()),job.backgroundSha256);
            try(ZipFile zip=new ZipFile(jobs.download(job.requestId).toFile());InputStream in=zip.getInputStream(zip.getEntry("dof-client.dll"))) {
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] part=new byte[4096];int n;while((n=in.read(part))!=-1)buffer.write(part,0,n);
                NativeConfigContainer.Document config=NativeConfigContainer.decode(buffer.toByteArray(),new byte[0]);
                assertArrayEquals(image.toByteArray(),config.entries.get(0).value());
            }
            request.backgroundBase64=null;
            try{jobs.submit(request,42);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            NativeClientJobs.Request bad=request(jobs);bad.backgroundBase64="%%%";
            try{jobs.submit(bad,42);fail();}catch(DofFailure e){assertEquals(400,e.status);}
            assertEquals(1,jobs.list().size());
        }
    }
    @Test public void spaceAdmissionRejectsNewWorkButKeepsExistingDownloadAndReplay() throws Exception {
        java.util.concurrent.atomic.AtomicLong usable = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
        try (Fixture f = new Fixture(); NativeClientJobs jobs = f.open(path -> usable.get())) {
            NativeClientJobs.Request old = request(jobs); jobs.submit(old,42);
            NativeClientJobs.Job done = finished(jobs,old.requestId); assertEquals("READY_FOR_TEST",done.state);
            usable.set(NativeClientJobs.BASE_RESERVE + NativeClientJobs.BUILD_PEAK - 1);
            NativeClientJobs.Request next = request(jobs);
            try { jobs.submit(next,42); fail(); } catch (DofFailure e) { assertEquals(507,e.status); }
            assertEquals(1,jobs.list().size());
            assertFalse(Files.exists(f.root.resolve("jobs").resolve(next.requestId)));
            assertEquals(done.sha256,jobs.submit(old,42).sha256);
            assertTrue(Files.exists(jobs.download(old.requestId)));
        }
    }
    @Test public void spaceLostDuringBuildFailsDurablyWithoutExposingPartialArchive() throws Exception {
        // First admission succeeds. Reject at worker start, before archive creation,
        // and after archive validation respectively. No disk-filling test fixture.
        for (int boundary : new int[]{2,3,4}) {
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            try (Fixture f = new Fixture()) {
                String id;
                try (NativeClientJobs jobs = f.open(path -> calls.incrementAndGet() < boundary ? Long.MAX_VALUE : 0)) {
                    NativeClientJobs.Request next=request(jobs); id=next.requestId; jobs.submit(next,42);
                    NativeClientJobs.Job failed=finished(jobs,id); assertEquals("FAILED",failed.state);
                    assertTrue(failed.error.contains("2 GiB")); assertEquals("FAILED",jobs.submit(next,42).state);
                    try { jobs.download(id); fail(); } catch(DofFailure e) { assertEquals(409,e.status); }
                    assertFalse(Files.exists(f.root.resolve("jobs").resolve(id).resolve("candidate.zip")));
                }
                try (NativeClientJobs restarted=f.open()) { assertEquals("FAILED",restarted.read(id).state); }
            }
        }
    }
}
