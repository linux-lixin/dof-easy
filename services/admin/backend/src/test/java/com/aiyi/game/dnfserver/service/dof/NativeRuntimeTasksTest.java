package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Synthetic mailbox faults only; native consumption is tested by the isolated stack. */
public class NativeRuntimeTasksTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final ObjectMapper json=new ObjectMapper();
    private final AtomicLong now=new AtomicLong(1700000000000L);
    private Path root,mail,audit;private NativeRuntimeTasks tasks;
    private final String database="00000000-0000-0000-0000-000000000001",boot="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private Map<String,Object> map(Object... values){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)out.put((String)values[i],values[i+1]);return out;}
    private static String sha(byte[] raw)throws Exception {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))s.append(String.format("%02x",b&255));return s.toString();}
    private void put(Path p,Map<String,Object> value)throws Exception{Files.write(p,json.writeValueAsBytes(value));}
    @SuppressWarnings("unchecked") private Map<String,Object> read(Path p)throws Exception{return json.readValue(Files.readAllBytes(p),Map.class);}
    @Before public void setup()throws Exception {
        root=temporary.newFolder("config").toPath();mail=Files.createDirectory(root.resolve(".runtime-apply"));audit=temporary.getRoot().toPath().resolve("audit");
        Files.write(root.resolve("anton-raid.ini"),("anton_name = 安徒恩\nanton_member_min = 1\nanton_open_day = 1,2,3,4,5,6,7\nanton_open_time = 00:00:00-23:59:59\n").getBytes(StandardCharsets.UTF_8));
        put(mail.resolve("binding.json"),map("schema",1,"instanceId","dof-test","databaseUuid",database,"pluginSHA256",NativeRuntimeTasks.PLUGIN_SHA256,"digestHelperSourceSHA256",NativeRuntimeTasks.HELPER_SOURCE_SHA256,"digestHelperSHA256",String.join("",Collections.nCopies(64,"0")),"digestHelperBytes",128,"executorScriptSHA256",NativeRuntimeTasks.EXECUTOR_SHA256,"gameAbiSHA256",NativeRuntimeTasks.GAME_ABI_SHA256,"channels",Arrays.asList("siroco15","siroco52")));
        heartbeat("siroco15",boot);heartbeat("siroco52",boot);tasks=restart();
    }
    private NativeRuntimeTasks restart(){return new NativeRuntimeTasks(root,audit,audit.resolve("files"),"dof-test",database,now::get);}
    private void heartbeat(String channel,String boot)throws Exception{put(mail.resolve("status-"+channel+".json"),map("schema",1,"instanceId","dof-test","databaseUuid",database,"bindingRevision",sha(Files.readAllBytes(mail.resolve("binding.json"))),"pluginSHA256",NativeRuntimeTasks.PLUGIN_SHA256,"channel",channel,"bootId",boot,"pid",20,"dispatcherThreadId",21,"idle",true,"updatedAt",now.get()));}
    private NativeRuntimeTasks.Edit edit()throws Exception{NativeRuntimeTasks.Edit e=new NativeRuntimeTasks.Edit();e.requestId=UUID.randomUUID().toString();e.revision=sha(Files.readAllBytes(root.resolve("anton-raid.ini")));return e;}
    private interface Action{void run()throws Exception;}
    private void reject(int status,Action action)throws Exception{try{action.run();fail("expected refusal");}catch(DofFailure e){assertEquals(status,e.status);}}
    @SuppressWarnings("unchecked") private void receipt(NativeRuntimeTasks.Edit e,String channel,String state)throws Exception{
        Map<String,Object> command=read(mail.resolve("request-anton.json"));Map<String,Object> r=map("schema",1,"instanceId","dof-test","databaseUuid",database,"channel",channel,"bootId",boot,"executorThreadId",21,"requestId",e.requestId,"module","anton","revision",e.revision,"bindingRevision",command.get("bindingRevision"),"state",state,"observed",command.get("expected"));put(mail.resolve("receipt-"+channel+"-anton.json"),r);
    }
    private void submit(NativeRuntimeTasks.Edit e)throws Exception{tasks.preview("anton",e,1);tasks.submit("anton",e,1);}
    @Test public void durablePreviewAndSubmitAreFrozenAndIdempotentAcrossRestart()throws Exception {
        NativeRuntimeTasks.Edit e=edit();assertEquals("PREVIEWED",tasks.preview("anton",e,1).get("state"));assertFalse(Files.exists(mail.resolve("request-anton.json")));
        NativeRuntimeTasks other=restart();assertEquals("SUBMITTED",other.submit("anton",e,1).get("state"));byte[] original=Files.readAllBytes(mail.resolve("request-anton.json"));
        assertEquals(NativeRuntimeTasks.HELPER_SOURCE_SHA256,((Map<?,?>)read(audit.resolve(e.requestId+".json")).get("executor")).get("digestHelperSourceSHA256"));
        assertFalse(read(mail.resolve("request-anton.json")).containsKey("executor"));
        now.addAndGet(1000);other.submit("anton",e,1);assertArrayEquals(original,Files.readAllBytes(mail.resolve("request-anton.json")));
        reject(409,()->other.submit("anton",e,2));e.revision=String.join("",Collections.nCopies(64,"0"));reject(409,()->other.preview("anton",e,1));
    }
    @Test public void staleConfigOrRestartedChannelNeverPublishes()throws Exception {
        NativeRuntimeTasks.Edit e=edit();tasks.preview("anton",e,1);Files.write(root.resolve("anton-raid.ini"),"different".getBytes(StandardCharsets.UTF_8));reject(409,()->tasks.submit("anton",e,1));
        assertFalse(Files.exists(mail.resolve("request-anton.json")));
    }
    @Test public void frozenBootTargetsCannotBeSilentlyUpdated()throws Exception {
        NativeRuntimeTasks.Edit e=edit();tasks.preview("anton",e,1);heartbeat("siroco52","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");reject(409,()->tasks.submit("anton",e,1));assertFalse(Files.exists(mail.resolve("request-anton.json")));
    }
    @Test public void missingStaleOrWrongInstanceCannotHideARequiredChannel()throws Exception {
        Files.delete(mail.resolve("status-siroco52.json"));assertEquals(false,tasks.status().get("webExecutionSupported"));reject(409,()->tasks.preview("anton",edit(),1));
        heartbeat("siroco52",boot);now.addAndGet(16000);reject(409,()->tasks.preview("anton",edit(),1));
        heartbeat("siroco15",boot);heartbeat("siroco52",boot);Map<String,Object> wrong=read(mail.resolve("status-siroco52.json"));wrong.put("databaseUuid","other");put(mail.resolve("status-siroco52.json"),wrong);reject(409,()->tasks.preview("anton",edit(),1));
    }
    @Test public void bothChannelReceiptsAndMatchingNativeGettersAreRequired()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);receipt(e,"siroco15","APPLIED");assertEquals("PARTIAL",tasks.reconcile("anton",e.requestId).get("state"));
        reject(409,()->tasks.preview("anton",edit(),1));receipt(e,"siroco52","APPLIED");assertEquals("APPLIED",tasks.reconcile("anton",e.requestId).get("state"));
        assertEquals(false,tasks.reconcile("anton",e.requestId).get("gameplayVerified"));assertEquals("APPLIED",restart().submit("anton",e,1).get("state"));
    }
    @Test public void oneRejectedChannelDoesNotReleaseAnotherChannelStillInFlight()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);receipt(e,"siroco15","REJECTED");assertEquals("PARTIAL",tasks.reconcile("anton",e.requestId).get("state"));reject(409,()->tasks.preview("anton",edit(),1));
        receipt(e,"siroco52","RUNNING");assertEquals("PARTIAL",tasks.reconcile("anton",e.requestId).get("state"));receipt(e,"siroco52","REJECTED");assertEquals("REJECTED",tasks.reconcile("anton",e.requestId).get("state"));
    }
    @Test public void forgedOrMismatchingReceiptsCannotBecomeSuccessful()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);receipt(e,"siroco15","APPLIED");Map<String,Object> r=read(mail.resolve("receipt-siroco15-anton.json"));r.put("revision",String.join("",Collections.nCopies(64,"0")));put(mail.resolve("receipt-siroco15-anton.json"),r);reject(409,()->tasks.reconcile("anton",e.requestId));
        receipt(e,"siroco15","APPLIED");r=read(mail.resolve("receipt-siroco15-anton.json"));r.put("observed",map("name","wrong"));put(mail.resolve("receipt-siroco15-anton.json"),r);reject(409,()->tasks.reconcile("anton",e.requestId));
    }
    @Test public void lostPublishReplyDoesNotRepublishFromReconciliation()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);byte[] command=Files.readAllBytes(mail.resolve("request-anton.json"));Map<String,Object> j=read(audit.resolve(e.requestId+".json"));j.put("state","PUBLISHING");put(audit.resolve(e.requestId+".json"),j);
        restart().reconcile("anton",e.requestId);assertArrayEquals(command,Files.readAllBytes(mail.resolve("request-anton.json")));
        Files.delete(mail.resolve("request-anton.json"));restart().submit("anton",e,1);assertFalse(Files.exists(mail.resolve("request-anton.json")));
    }
    @Test public void unknownResultsRequireExpiredTaskFreshIdleChannelsAndAnAuditedNewDecision()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);receipt(e,"siroco15","RUNNING");now.addAndGet(120001);Map<String,Object> pending=tasks.reconcile("anton",e.requestId);assertEquals("UNKNOWN",pending.get("state"));
        NativeRuntimeTasks.Resolve decision=new NativeRuntimeTasks.Resolve();decision.requestId=UUID.randomUUID().toString();decision.keepCurrent=true;decision.taskRevision=(String)pending.get("taskRevision");reject(409,()->tasks.resolve("anton",e.requestId,decision,2));
        heartbeat("siroco15",boot);heartbeat("siroco52",boot);byte[] before=Files.readAllBytes(audit.resolve(e.requestId+".json"));assertEquals("KEEP_CURRENT",tasks.resolve("anton",e.requestId,decision,2).get("state"));
        assertEquals("NOT_CONFIRMED",read(audit.resolve(e.requestId+".json")).get("state"));
        // Crash after the decision is durable but before the parent is closed.
        Files.write(audit.resolve(e.requestId+".json"),before);restart().resolve("anton",e.requestId,decision,2);assertEquals("NOT_CONFIRMED",read(audit.resolve(e.requestId+".json")).get("state"));
        tasks.preview("anton",edit(),1);
    }
    @Test public void aSharedGameLockBlocksBothFileEditsAndRefreshPublication()throws Exception {
        NativeRuntimeTasks.Edit e=edit();tasks.preview("anton",e,1);
        try(FileChannel c=FileChannel.open(root.resolve(".admin-operation.lock"),StandardOpenOption.WRITE);FileLock held=c.lock()){
            reject(409,()->tasks.submit("anton",e,1));NativePluginFiles files=new NativePluginFiles(root,audit.resolve("files"),"dof-test");NativePluginFiles.Edit change=new NativePluginFiles.Edit();change.requestId=UUID.randomUUID().toString();change.revision=e.revision;change.values=Collections.singletonMap("anton_member_min","2");reject(409,()->files.save("anton",change,1,null));
        }
        assertFalse(Files.exists(mail.resolve("request-anton.json")));
    }
    @Test public void symlinkedStatusAndUnverifiedEncodingAreRejected()throws Exception {
        Path status=mail.resolve("status-siroco15.json"),outside=temporary.newFile("outside").toPath();Files.delete(status);Files.createSymbolicLink(status,outside);reject(409,()->tasks.preview("anton",edit(),1));
        Files.delete(status);heartbeat("siroco15",boot);String text=new String(Files.readAllBytes(root.resolve("anton-raid.ini")),StandardCharsets.UTF_8);Files.write(root.resolve("anton-raid.ini"),text.getBytes(Charset.forName("GB18030")));reject(409,()->tasks.preview("anton",edit(),1));
    }
    @Test public void corruptAuditStateOrTargetNeverMakesAnUnresolvedTaskDisappear()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);Map<String,Object> j=read(audit.resolve(e.requestId+".json"));j.put("state","invented-success");put(audit.resolve(e.requestId+".json"),j);reject(409,()->tasks.preview("anton",edit(),1));
    }
    @Test public void missingExecutorLeavesFileManagementAvailableWithoutFakeRuntimeSuccess()throws Exception {
        Files.delete(mail.resolve("binding.json"));assertEquals(false,tasks.status().get("webExecutionSupported"));NativePluginFiles files=new NativePluginFiles(root,audit.resolve("files"),"dof-test");assertNotNull(files.read("anton").get("revision"));
    }
    @Test public void nativeByteBufferLimitsAndIntegerNormalizationAreExplicit()throws Exception {
        Path p=root.resolve("anton-raid.ini");String original=new String(Files.readAllBytes(p),StandardCharsets.UTF_8);
        Files.write(p,original.replace("= 安徒恩","= "+String.join("",Collections.nCopies(100,"安"))).getBytes(StandardCharsets.UTF_8));reject(409,()->tasks.preview("anton",edit(),1));
        Files.write(p,original.replace("min = 1","min = 01").getBytes(StandardCharsets.UTF_8));Map<String,Object> result=tasks.preview("anton",edit(),1);assertEquals("1",((Map<?,?>)result.get("expected")).get("member_min"));
    }
    @Test public void unresolvedConfigurationSaveBlocksNewNativeRefreshEvenWhenBytesAreReadable()throws Exception {
        NativePluginFiles files=new NativePluginFiles(root,audit.resolve("files"),"dof-test");NativePluginFiles.Edit change=new NativePluginFiles.Edit();change.requestId=UUID.randomUUID().toString();change.revision=edit().revision;change.values=Collections.singletonMap("anton_member_min","2");files.save("anton",change,1,null);
        Path record=audit.resolve("files").resolve(change.requestId+".json");Map<String,Object> j=read(record);j.put("state","PREPARED");put(record,j);reject(409,()->tasks.preview("anton",edit(),1));
        files.reconcile("anton",change.requestId);assertEquals("PREVIEWED",tasks.preview("anton",edit(),1).get("state"));
    }
    @Test public void snapshotRevisionBelongsToTheRecordReadRatherThanALaterFilesystemVersion()throws Exception {
        NativeRuntimeTasks.Edit e=edit();submit(e);Path p=audit.resolve(e.requestId+".json");String original=sha(Files.readAllBytes(p));
        Map<?,?> snapshot=org.springframework.test.util.ReflectionTestUtils.invokeMethod(tasks,"journal",e.requestId);
        Files.delete(p);
        Map<?,?> response=org.springframework.test.util.ReflectionTestUtils.invokeMethod(tasks,"result",snapshot);
        assertEquals(original,response.get("taskRevision"));assertEquals("SUBMITTED",response.get("state"));assertFalse(response.containsKey("fileRevision"));
    }
}
