package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.Assert.*;

public class NativeScriptMetadataTest {
    private static final String A="0123456789abcdef0123456789abcdef", B="abcdef0123456789abcdef0123456789";

    @Test public void matchesOriginalLoaderVectorsWithinSafeFilenameScope() throws Exception {
        JsonNode report=new ObjectMapper().readTree(PrivateNativeFixtures.open("script-metadata-native-vectors.json"));
        int compared=0;
        for (JsonNode row:report.get("cases")) {
            if (!row.get("acceptedStructure").asBoolean()) {rejected(row.get("wire").asText());continue;}
            JsonNode controls=row.get("controls");
            if (!controls.get("0x16030cc0").asText().endsWith(".pvf")) continue;
            NativeScriptMetadata value=NativeScriptMetadata.parse(row.get("wire").asText());
            assertEquals(controls.get("0x16030cc2").asText()+"^^"+controls.get("0x16030cc0").asText(),value.encode());
            assertEquals(controls.get("0x16030cc0").asText(),value.filename());compared++;
        }
        assertEquals(4,compared);
    }

    @Test public void preservesOpaqueContextAndFilenameWhenReplacingDigest() {
        for (String context:new String[]{"","0","opaque-test","未知上下文","opaque^single"}) {
            String wire=A+"^^"+context+"^^测试.pvf";
            NativeScriptMetadata value=NativeScriptMetadata.parse(wire);
            assertEquals(wire,value.encode());assertTrue(value.hasChecksum());assertEquals("测试.pvf",value.filename());
            assertEquals(B+"^^"+context+"^^测试.pvf",value.withChecksum(B).encode());
            assertFalse(value.toString().contains("未知上下文"));
        }
    }

    @Test public void seedDoesNotClaimArtifactVerification() {
        NativeScriptMetadata seed=NativeScriptMetadata.parse("0^^0^^Script.pvf");
        assertFalse(seed.hasChecksum());assertEquals("0^^0^^Script.pvf",seed.encode());
        assertTrue(seed.withChecksum(A).hasChecksum());
        assertEquals(A+"^^0^^Script.pvf",seed.withChecksum(A).encode());
    }

    @Test public void rejectsAmbiguousMetadataAndUnsafeClientNames() {
        for (String wire:new String[]{null,"",A,A+"^^x",A+"^^x^^Script.pvf^^more","^^^^",A+"^^x^^",
                A.toUpperCase()+"^^^^Script.pvf",A+"^^a\nb^^Script.pvf",A+"^^\u0000^^Script.pvf",A+"^^😀^^Script.pvf"}) rejected(wire);
        for (String name:new String[]{"../Script.pvf","a/Script.pvf","a\\Script.pvf","C:Script.pvf",".hidden.pvf",
                " Script.pvf","Script.pvf ","con.pvf","LPT1.pvf","nul.any.pvf","Script.txt","a?.pvf","x|x.pvf"})
            rejected(A+"^^^^"+name);
        try {NativeScriptMetadata.parse(A+"^^^^Script.pvf").withChecksum("0");fail();} catch (IllegalArgumentException expected) { }
    }
    private static void rejected(String wire) {
        try {NativeScriptMetadata.parse(wire);fail("Accepted invalid metadata");} catch (IllegalArgumentException expected) { }
    }
}
