package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class NativeScriptChecksumTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private byte[] hex(String text) {
        byte[] out=new byte[text.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return out;
    }
    @Test public void originalCompressedAndUncompressedTailVectorsMatch() throws Exception {
        JsonNode root=new ObjectMapper().readTree(PrivateNativeFixtures.open("script-tail-native-vectors.json"));
        assertEquals(2,root.get("vectors").size());
        for(JsonNode v:root.get("vectors")) {
            byte[] tail=hex(v.get("hex").asText());assertEquals(100,tail.length);
            assertEquals(v.get("digest").asText(),NativeScriptChecksum.digest(tail));
            Path file=temporary.newFile().toPath();byte[] data=new byte[2048];Arrays.fill(data,(byte)7);
            System.arraycopy(tail,0,data,data.length-100,100);Files.write(file,data);
            assertTrue(NativeScriptChecksum.matches(file,v.get("digest").asText()));
        }
    }
    @Test public void checksumCoversOnlyTailAndMustNotReplaceWholeArtifactIntegrity() throws Exception {
        Path file=temporary.newFile().toPath();byte[] data=new byte[300];new Random(20261025).nextBytes(data);Files.write(file,data);
        String first=NativeScriptChecksum.calculate(file),sha=NativeUiState.sha256(data);
        data[0]^=1;Files.write(file,data);
        assertEquals(first,NativeScriptChecksum.calculate(file));assertNotEquals(sha,NativeUiState.sha256(data));
        data[data.length-1]^=1;Files.write(file,data);assertFalse(NativeScriptChecksum.matches(file,first));
        Files.write(file,new byte[]{1},StandardOpenOption.APPEND);assertFalse(NativeScriptChecksum.matches(file,first));
    }
    @Test public void smallMissingDirectorySymlinkAndBadExpectedValueAreRejected() throws Exception {
        Path file=temporary.newFile().toPath();Files.write(file,new byte[99]);
        for(Path invalid:Arrays.asList(file,temporary.getRoot().toPath(),temporary.getRoot().toPath().resolve("missing"))) {
            try{NativeScriptChecksum.calculate(invalid);fail();}catch(IOException expected){}
        }
        Files.write(file,new byte[100]);assertEquals("6d0bb00954ceb7fbee436bb55a8397a9",NativeScriptChecksum.calculate(file));
        Path link=temporary.getRoot().toPath().resolve("symlink");Files.createSymbolicLink(link,file);
        try{NativeScriptChecksum.calculate(link);fail();}catch(IOException expected){}
        for(String invalid:Arrays.asList(null,"","abc","6D0BB00954CEB7FBEE436BB55A8397A9","../../Script.pvf")) {
            try{NativeScriptChecksum.matches(file,invalid);fail();}catch(IllegalArgumentException expected){}
        }
    }
}
