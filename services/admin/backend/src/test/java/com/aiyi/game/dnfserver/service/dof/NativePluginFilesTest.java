package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class NativePluginFilesTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Path root,audit;
    private NativePluginFiles files;
    private static byte[] fixture(){return ("// operator comment\r\nanton_name = 安徒恩\r\nanton_member_min = 1\r\nunknown = preserve-me\r\nteam_reward1 = [3340 100 -1 0]\r\n").getBytes(StandardCharsets.UTF_8);}
    @Before public void setup()throws Exception {
        root=temporary.newFolder("config").toPath();audit=temporary.getRoot().toPath().resolve("audit");
        Files.write(root.resolve("anton-raid.ini"),fixture());files=new NativePluginFiles(root,audit,"dof-test");
    }
    private NativePluginFiles.Edit edit(String key,String value)throws IOException {
        NativePluginFiles.Edit e=new NativePluginFiles.Edit();e.requestId=UUID.randomUUID().toString();e.revision=(String)files.read("anton").get("revision");e.values=Collections.singletonMap(key,value);return e;
    }
    private interface Action {void run()throws Exception;}
    private void rejects(int status,Action action)throws Exception{try{action.run();fail("expected failure");}catch(DofFailure e){assertEquals(status,e.status);}}
    @Test public void editsPreserveEncodingCommentsUnknownFieldsAndNewlines()throws Exception {
        byte[] original=fixture();Map<String,String> patch=Collections.singletonMap("anton_member_min","2");
        byte[] changed=NativePluginConfig.apply("anton",original,patch);
        assertArrayEquals(new String(original,StandardCharsets.UTF_8).replace("min = 1","min = 2").getBytes(StandardCharsets.UTF_8),changed);
        assertArrayEquals(original,NativePluginConfig.apply("anton",original,Collections.singletonMap("anton_member_min","1")));
        Charset gbk=Charset.forName("GB18030");byte[] legacy=new String(original,StandardCharsets.UTF_8).getBytes(gbk);
        assertArrayEquals(new String(legacy,gbk).replace("min = 1","min = 2").getBytes(gbk),NativePluginConfig.apply("anton",legacy,patch));
        byte[] inline="\uFEFFanton_member_min = 1  // keep comment\r\n".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals("\uFEFFanton_member_min = 2  // keep comment\r\n".getBytes(StandardCharsets.UTF_8),NativePluginConfig.apply("anton",inline,patch));
    }
    @Test public void actualTemplatesRoundTripAndEveryDeclaredDefaultIsAccepted()throws Exception {
        Path repo=Paths.get("").toAbsolutePath();while(repo!=null && !Files.exists(repo.resolve("services/game/resources/native/config/plugin.ini")))repo=repo.getParent();assertNotNull(repo);
        int fields=0;
        for(NativePluginConfig.Module m:NativePluginConfig.modules()) {
            Path source=repo.resolve(m.id.equals("plugin")?"services/game/resources/native/config/plugin.ini":"services/game/resources/plugins/raid/config/"+m.file);
            byte[] before=Files.readAllBytes(source);Map<String,String> values=NativePluginConfig.parse(before).values();
            for(NativePluginConfig.Field f:m.fields){assertTrue(values.containsKey(f.key));NativePluginConfig.validate(f,values.get(f.key));fields++;}
            assertArrayEquals(before,NativePluginConfig.apply(m.id,before,values));
        }
        assertEquals(85,fields);
    }
    @Test public void malformedRewardsUnknownFieldsAndImportInjectionDoNotWrite()throws Exception {
        for(String rewards:Arrays.asList("[1 2 100001 0]","[1 2 -1 0 3 4 -1 0]","[1 2 1 7]","[1 -1 2 0]","[1 2 3]"))
            rejects(400,()->files.preview("anton",edit("team_reward1",rewards)));
        rejects(400,()->files.preview("anton",edit("unknown","changed")));
        rejects(400,()->files.preview("anton",edit("anton_name","name\nunknown = evil")));
        NativePluginFiles.Edit e=edit("anton_name","unused");e.values=null;e.content=new String(fixture(),StandardCharsets.UTF_8)+"injected=1\n";
        rejects(400,()->files.preview("anton",e));
        e.content=new String(fixture(),StandardCharsets.UTF_8).replace("preserve-me","changed");rejects(400,()->files.preview("anton",e));
        e.content=new String(fixture(),StandardCharsets.UTF_8).replace("安徒恩","\uD800");rejects(400,()->files.preview("anton",e));
        assertArrayEquals(fixture(),Files.readAllBytes(root.resolve("anton-raid.ini")));
    }
    @Test public void timeAndDayAndTupleBoundsAreValidated()throws Exception {
        byte[] data="anton_open_time = 00:00:00-23:59:59\nanton_open_day = 1,2,3\nanton_area_info = 17 109\n".getBytes(StandardCharsets.UTF_8);
        for(Map<String,String> patch:Arrays.asList(Collections.singletonMap("anton_open_time","24:00:00-00:00:00"),Collections.singletonMap("anton_open_day","1,1"),Collections.singletonMap("anton_open_day","0,7"),Collections.singletonMap("anton_area_info","17 2 109")))
            rejects(400,()->NativePluginConfig.apply("anton",data,patch));
    }
    @Test public void nativeStringBuffersAreBoundedByActualFileEncodingAndReserveTerminator()throws Exception {
        String ascii=String.join("",Collections.nCopies(254,"a"));assertNotNull(NativePluginConfig.apply("anton",fixture(),Collections.singletonMap("anton_name",ascii)));
        rejects(400,()->NativePluginConfig.apply("anton",fixture(),Collections.singletonMap("anton_name",ascii+"a")));
        rejects(400,()->NativePluginConfig.apply("anton",fixture(),Collections.singletonMap("anton_name",String.join("",Collections.nCopies(85,"安")))));
        String c1=String.join("",Collections.nCopies(64,"\u0080"));assertNotNull(NativePluginConfig.apply("anton",fixture(),Collections.singletonMap("anton_name",c1)));
        byte[] gb=new String(fixture(),StandardCharsets.UTF_8).getBytes(Charset.forName("GB18030"));rejects(400,()->NativePluginConfig.apply("anton",gb,Collections.singletonMap("anton_name",c1)));
        byte[] day="anton_open_day = 1,2,3\n".getBytes(StandardCharsets.UTF_8);
        rejects(400,()->NativePluginConfig.apply("anton",day,Collections.singletonMap("anton_open_day","1"+String.join("",Collections.nCopies(300," "))+",2")));
    }
    @Test public void previewSaveIdempotencyAndTargetedRestore()throws Exception {
        NativePluginFiles.Edit e=edit("anton_member_min","2");assertEquals(false,files.preview("anton",e).get("persisted"));
        assertArrayEquals(fixture(),Files.readAllBytes(root.resolve("anton-raid.ini")));
        assertEquals("PERSISTED",files.save("anton",e,1,null).get("state"));
        assertEquals("PERSISTED",files.save("anton",e,1,null).get("state"));
        rejects(409,()->files.save("anton",e,2,null));
        NativePluginFiles.Edit other=edit("anton_name","自定义团本");files.save("anton",other,1,null);
        NativePluginFiles.Edit restore=edit("anton_name","unused");restore.values=null;
        assertEquals("PERSISTED",files.save("anton",restore,1,e.requestId).get("state"));
        Map<String,String> now=NativePluginConfig.parse(Files.readAllBytes(root.resolve("anton-raid.ini"))).values();
        assertEquals("1",now.get("anton_member_min"));assertEquals("自定义团本",now.get("anton_name"));
        assertEquals(3,files.history("anton").size());
        for(Map<String,Object> row:files.history("anton")){assertFalse(row.containsKey("before"));assertEquals("not_observed",row.get("effectState"));}
    }
    @Test public void importsKeepOriginalCommentsAndNoChangeIsAudited()throws Exception {
        NativePluginFiles.Edit e=edit("anton_name","unused");e.values=null;e.content=new String(fixture(),StandardCharsets.UTF_8).replace("operator comment","new comment").replace("min = 1","min = 2");
        files.save("anton",e,1,null);
        assertTrue(new String(Files.readAllBytes(root.resolve("anton-raid.ini")),StandardCharsets.UTF_8).contains("// operator comment"));
        assertEquals("NO_CHANGE",files.save("anton",edit("anton_member_min","2"),1,null).get("state"));
    }
    @Test public void conflictsAndCrossModuleRestoreAreRejected()throws Exception {
        NativePluginFiles.Edit stale=edit("anton_member_min","3"),first=edit("anton_member_min","2");files.save("anton",first,1,null);
        rejects(409,()->files.save("anton",stale,1,null));
        files.save("anton",edit("anton_member_min","4"),1,null);
        NativePluginFiles.Edit restore=edit("anton_name","unused");restore.values=null;
        rejects(409,()->files.save("anton",restore,1,first.requestId));
        Files.write(root.resolve("luke-raid.ini"),"luke_name = 卢克\n".getBytes(StandardCharsets.UTF_8));
        restore.revision=(String)files.read("luke").get("revision");rejects(409,()->files.save("luke",restore,1,first.requestId));
    }
    @Test public void interruptedWriteCanBeReconciledAfterServiceRestart()throws Exception {
        NativePluginFiles.Edit e=edit("anton_member_min","2");files.save("anton",e,1,null);
        ObjectMapper json=new ObjectMapper();Path path=audit.resolve(e.requestId+".json");
        NativePluginFiles.Journal j=json.readValue(Files.readAllBytes(path),NativePluginFiles.Journal.class);j.state="PREPARED";Files.write(path,json.writeValueAsBytes(j));
        NativePluginFiles restart=new NativePluginFiles(root,audit,"dof-test");
        rejects(409,()->restart.save("anton",edit("anton_name","changed"),1,null));
        assertEquals("PERSISTED",restart.reconcile("anton",e.requestId).get("state"));
        j.state="PREPARED";Files.write(path,json.writeValueAsBytes(j));Files.write(root.resolve("anton-raid.ini"),fixture());
        assertEquals("NOT_APPLIED",restart.reconcile("anton",e.requestId).get("state"));
    }
    @Test public void corruptedJournalAndSymbolicLinksAndConcurrentWriterStopChanges()throws Exception {
        NativePluginFiles.Edit e=edit("anton_member_min","2");files.save("anton",e,1,null);
        try(FileChannel channel=FileChannel.open(root.resolve(".admin-operation.lock"),StandardOpenOption.WRITE);FileLock held=channel.lock()) {
            rejects(409,()->files.save("anton",edit("anton_member_min","3"),1,null));
        }
        Path record=audit.resolve(e.requestId+".json");Files.write(record,"{}".getBytes(StandardCharsets.UTF_8));rejects(409,()->files.history("anton"));
        Path destination=root.resolve("anton-raid.ini");Path outside=temporary.newFile("outside.ini").toPath();Files.write(outside,fixture());
        Files.delete(destination);Files.createSymbolicLink(destination,outside);rejects(409,()->files.read("anton"));assertArrayEquals(fixture(),Files.readAllBytes(outside));
    }
}
