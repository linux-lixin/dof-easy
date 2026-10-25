package com.aiyi.game.dnfserver.service.dof;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.Assert.*;
public class NativeUiPrivateProfileTest {
    @Test public void privateMigrationIsExplicitAndCannotOverwriteAnotherState() throws Exception {
        String name="dof.legacy.ui.profile", old=System.getProperty(name);
        Path file=Files.createTempFile("ui-private-synthetic-", ".json");
        try {
            System.setProperty(name, "");
            Map<String,String> before=new LinkedHashMap<>();
            before.put("Hotkeys","synthetic-before"); before.put("temp_2","0##0"); before.put("temp_3",null);
            NativeUiState state=NativeUiState.fromText(before);
            assertFalse(new NativeUiProfiles().compatible(state));
            Map<String,String> after=new LinkedHashMap<>(before); after.put("Hotkeys","synthetic-after");
            Map<String,Object> document=new LinkedHashMap<>();document.put("before",before);document.put("after",after);
            new ObjectMapper().writeValue(file.toFile(),document);
            System.setProperty(name,file.toString());
            NativeUiProfiles profile=new NativeUiProfiles();
            assertTrue(profile.compatible(state));assertTrue(profile.is100(profile.apply("native-100",state)));
            before.put("Hotkeys","unrelated-state");
            try {profile.apply("native-100",NativeUiState.fromText(before));fail("Must preserve unrelated state");}catch(DofFailure expected){}
            Files.write(file,new byte[]{'{'});
            try {new NativeUiProfiles();fail("Reject malformed private profile");}catch(IllegalStateException expected){}
        } finally {Files.deleteIfExists(file);if(old==null)System.clearProperty(name);else System.setProperty(name,old);}
    }
}
