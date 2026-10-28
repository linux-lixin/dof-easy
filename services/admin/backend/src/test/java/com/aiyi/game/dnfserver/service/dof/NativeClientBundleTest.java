package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.security.KeyPair;
import java.util.*;
import java.util.zip.*;

public class NativeClientBundleTest {
    @Test public void sourceParameterOrVerificationChangeCannotBeAppliedAsSameBatch() {
        NativeScriptJobs.Job script = new NativeScriptJobs.Job();
        script.requestId = UUID.randomUUID().toString(); script.sourceSha256 = "source";
        script.sha256 = "artifact"; script.parameterSha256 = "parameter"; script.nativeChecksum = "checksum";
        script.roundTripVerified = true;
        NativeClientJobs.Job bundle = new NativeClientJobs.Job(); bundle.manifestVersion = 1;
        bundle.scriptBatch = new LinkedHashMap<>();
        bundle.scriptBatch.put("taskId",script.requestId); bundle.scriptBatch.put("sourceSha256","source");
        bundle.scriptBatch.put("artifactSha256","artifact"); bundle.scriptBatch.put("parameterSha256","parameter");
        bundle.scriptBatch.put("nativeChecksum","checksum"); bundle.scriptBatch.put("compressed",false);
        bundle.scriptBatch.put("roundTripVerified",true);
        NativeClientJobs.requireScriptBatch(bundle,script);
        for (String key : Arrays.asList("taskId","sourceSha256","artifactSha256","parameterSha256","nativeChecksum","compressed","roundTripVerified")) {
            Object before = bundle.scriptBatch.put(key,"changed");
            try { NativeClientJobs.requireScriptBatch(bundle,script); fail(key); }
            catch (DofFailure e) { assertEquals(409,e.status); }
            bundle.scriptBatch.put(key,before);
        }
        bundle.scriptBatch = null;
        try { NativeClientJobs.requireScriptBatch(bundle,script); fail(); }
        catch (DofFailure e) { assertEquals(409,e.status); }
        bundle.manifestVersion = 0;
        NativeClientJobs.requireScriptBatch(bundle,script); // Explicit compatibility, no new provenance claim.
    }

    @Test public void pinnedClientBundleContainsExactlySixFilesAndNoPrivateParameter()throws Exception {
        String dll=System.getProperty("dof.script.clientDll");Assume.assumeNotNull(dll);
        Path root=Files.createTempDirectory("dof_native-script-bundle-");
        try {
            Path inputs=Files.createDirectory(root.resolve("inputs")),scriptRoot=Files.createDirectory(root.resolve("script"));
            KeyPair identity=NativeClientPayloadTest.pair();Map<String,byte[]> files=new LinkedHashMap<>();
            files.put("template.dll",NativeConfigContainer.encode(NativeClientPublicationTest.fixture(NativeClientPayloadTest.pair()),new byte[0]));
            files.put("game-public.der",identity.getPublic().getEncoded());files.put("dof-launcher.exe",new byte[]{1,2,3});files.put("ijl15.dll",Files.readAllBytes(Paths.get(dll)));
            NativeClientJobs.Binding b=new NativeClientJobs.Binding();b.instanceId="isolated-bundle-test";b.environment="isolated";b.adapterVersion=DofAdminService.VERSION;b.clientFormatVersion=NativeClientFormat.VERSION;b.sha256=new LinkedHashMap<>();
            b.databaseUuid="00000000-0000-4000-8000-000000000001";b.readerRecipe="dof-launcher-reader-2.2";
            for(Map.Entry<String,byte[]> f:files.entrySet()){Files.write(inputs.resolve(f.getKey()),f.getValue());b.sha256.put(f.getKey(),NativeUiState.sha256(f.getValue()));}
            files.clear();new ObjectMapper().writeValue(inputs.resolve("binding.json").toFile(),b);
            try(NativeScriptJobs scripts=new NativeScriptJobs(scriptRoot,b.instanceId,p->Long.MAX_VALUE);
                NativeClientJobs client=new NativeClientJobs(root,b.instanceId,p->Long.MAX_VALUE,scripts::artifact)) {
                String revision=client.status().get("revision").toString();byte[] raw=NativeScriptJobsTest.fixture(3);
                NativeScriptJobs.Request r=new NativeScriptJobs.Request();r.requestId=UUID.randomUUID().toString();r.uploadId=UUID.randomUUID().toString();r.revision=revision;
                scripts.upload(r.uploadId,revision,42,raw.length,new ByteArrayInputStream(raw));scripts.submit(r,42);
                assertEquals("READY_FOR_TEST",NativeScriptJobsTest.finished(scripts,r.requestId).state);
                NativeClientJobs.Request pack=new NativeClientJobs.Request();pack.requestId=UUID.randomUUID().toString();pack.revision=revision;pack.endpoint="192.0.2.45";pack.scriptTaskId=r.requestId;client.submit(pack,42);
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(45);NativeClientJobs.Job j;
                do{j=client.read(pack.requestId);if(!Arrays.asList("QUEUED","RUNNING").contains(j.state))break;Thread.sleep(30);}while(System.nanoTime()<until);
                assertEquals(j.error,"READY_FOR_TEST",j.state);assertEquals("isolated-dof-pvf-bundle",j.packageKind);
                try(ZipFile zip=new ZipFile(client.download(pack.requestId).toFile())) {
                    Set<String> names=new HashSet<>();zip.stream().forEach(e->names.add(e.getName()));
                    assertEquals(new HashSet<>(Arrays.asList("dof-launcher.exe","ijl15.dll","dof-client.dll","Script.pvf","manifest.json","使用与恢复.txt")),names);
                    byte[] manifest=new byte[(int)zip.getEntry("manifest.json").getSize()];try(InputStream in=zip.getInputStream(zip.getEntry("manifest.json"))){new DataInputStream(in).readFully(manifest);}
                    String text=new String(manifest,java.nio.charset.StandardCharsets.UTF_8);assertFalse(text.contains(scripts.parameters(r.requestId)));assertFalse(text.contains("TEST-DB-SECRET"));assertTrue(text.contains(j.scriptSha256));
                    assertEquals(raw.length+1280,zip.getEntry("Script.pvf").getSize());
                    Map<?,?> parsed=new ObjectMapper().readValue(manifest,Map.class);
                    assertEquals(b.databaseUuid,parsed.get("databaseUuid"));
                    assertEquals(b.readerRecipe,parsed.get("readerRecipe"));
                    assertEquals(b.sha256,parsed.get("sourceSha256"));
                    assertEquals(r.requestId,parsed.get("scriptTaskId"));
                    assertEquals(j.scriptBatch,parsed.get("scriptBatch"));
                    assertEquals(j.scriptSha256,parsed.get("scriptArtifactSha256"));
                    assertEquals(scripts.read(r.requestId).parameterSha256,parsed.get("scriptParameterSha256"));
                    assertEquals(Boolean.FALSE,parsed.get("scriptCompressed"));
                    assertEquals(Boolean.TRUE,parsed.get("scriptRoundTripVerified"));
                    Map<?,?> lengths=(Map<?,?>)parsed.get("bytes");
                    for(String name:names)if(!name.equals("manifest.json"))assertEquals(zip.getEntry(name).getSize(),((Number)lengths.get(name)).longValue());
                }
                pack.scriptTaskId=null;try{client.submit(pack,42);fail();}catch(DofFailure e){assertEquals(409,e.status);}
            }
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
}
