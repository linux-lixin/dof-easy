package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class NativeScriptFileCodecTest {
    private byte[] source() {
        byte[] data=new byte[56+256+43];new Random(12).nextBytes(data);
        ByteBuffer h=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(36).put("11111111-2222-3333-4444-555555555555".getBytes(StandardCharsets.US_ASCII));
        h.putInt(0xf851).putInt(256).putInt(0x98765432).putInt(0);return data;
    }
    private byte[] trailer() {byte[] t=new byte[1280];Arrays.fill(t,1024,1280,(byte)'x');return t;}
    private String sha(byte[] bytes)throws Exception {
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();
    }
    private byte[] read(InputStream in,int n)throws IOException {byte[] b=new byte[n];new DataInputStream(in).readFully(b);return b;}
    @Test public void completeCandidatesReverseAndRetainExactResourcesInBothModes() throws Exception {
        byte[] original=source();
        for(boolean compressed:new boolean[]{false,true}) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            NativeScriptFileCodec.Result r=NativeScriptFileCodec.encode(new ByteArrayInputStream(original),out,original.length,sha(original),0x12345678,compressed,trailer());
            assertEquals(sha(out.toByteArray()),r.artifactSha256);assertEquals(original.length,r.sourceBytes);assertEquals(out.size(),r.artifactBytes);
            PushbackInputStream wire=new PushbackInputStream(new ByteArrayInputStream(out.toByteArray()),65536);
            assertArrayEquals(Arrays.copyOf(original,56),read(wire,56));ByteArrayOutputStream index=new ByteArrayOutputStream();
            if(compressed)NativeScriptCompression.decodeIndex(wire,index,256,0x98765432,0x12345678);
            else NativeScriptIndex.transfer(wire,index,256,0x98765432,0x12345678,false);
            assertArrayEquals(Arrays.copyOfRange(original,56,312),index.toByteArray());
            assertArrayEquals(Arrays.copyOfRange(original,312,original.length),read(wire,43));
            assertArrayEquals(trailer(),read(wire,1280));assertEquals(-1,wire.read());
        }
    }
    @Test public void sameNativeChecksumCannotHideChangedContentOrMask() throws Exception {
        byte[] original=source();NativeScriptFileCodec.Result[] results=new NativeScriptFileCodec.Result[3];
        for(int i=0;i<3;i++) {
            byte[] input=original.clone();if(i==1)input[input.length-1]^=1;
            results[i]=NativeScriptFileCodec.encode(new ByteArrayInputStream(input),new ByteArrayOutputStream(),input.length,sha(input),i==2?9:8,false,trailer());
        }
        assertEquals(results[0].nativeChecksum,results[1].nativeChecksum);assertEquals(results[0].nativeChecksum,results[2].nativeChecksum);
        assertNotEquals(results[0].artifactSha256,results[1].artifactSha256);assertNotEquals(results[0].artifactSha256,results[2].artifactSha256);
        try {NativeScriptFileCodec.encode(new ByteArrayInputStream(original),new ByteArrayOutputStream(),original.length,results[1].sourceSha256,8,false,trailer());fail();}
        catch(IOException expected) {assertTrue(expected.getMessage().contains("来源校验"));}
    }
    @Test public void malformedHeaderAndTailFailBeforeAnyOutput() throws Exception {
        for(int offset:new int[]{0,4,40,44,52}) {
            byte[] input=source();Arrays.fill(input,offset,offset+4,(byte)255);ByteArrayOutputStream output=new ByteArrayOutputStream();
            try {NativeScriptFileCodec.encode(new ByteArrayInputStream(input),output,input.length,sha(input),0,false,trailer());fail();}
            catch(IOException expected) {assertEquals(0,output.size());}
        }
        byte[] bad=trailer();bad[0]=1;ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {NativeScriptFileCodec.encode(new ByteArrayInputStream(source()),out,source().length,sha(source()),0,false,bad);fail();}
        catch(IllegalArgumentException expected) {assertEquals(0,out.size());}
    }
    @Test public void truncationLengthChangeCancellationAndOutputFailureAreNotSuccess() throws Exception {
        byte[] original=source();
        for(int length:new int[]{original.length-1,original.length+1}) {
            try {NativeScriptFileCodec.encode(new ByteArrayInputStream(Arrays.copyOf(original,length)),new ByteArrayOutputStream(),original.length,sha(original),0,false,trailer());fail();}
            catch(IOException expected) { }
        }
        Thread.currentThread().interrupt();
        try {NativeScriptFileCodec.encode(new ByteArrayInputStream(original),new ByteArrayOutputStream(),original.length,sha(original),0,false,trailer());fail();}
        catch(InterruptedIOException expected) { } finally {Thread.interrupted();}
        try {NativeScriptFileCodec.encode(new ByteArrayInputStream(original),new OutputStream(){public void write(int b)throws IOException {throw new IOException("disk full");}},original.length,sha(original),0,false,trailer());fail();}
        catch(IOException expected) {assertEquals("disk full",expected.getMessage());}
    }
    @Test public void twoCompleteOriginalArtifactsMatchIncludingNativeTrailersWhenProvided() throws Exception {
        String path=System.getProperty("qingkong.script.pairDirectory");Assume.assumeNotNull(path);
        Path dir=Paths.get(path),source=dir.resolve("Script.pvf");
        String sourceHash="2e65c17d075afec73203ba8f2bc9f5508f3b44706300f4ae7f9c13b5e0687fc5";
        JsonNode analysis=new ObjectMapper().readTree(dir.resolve("compressed-analysis.private.json").toFile());
        for(boolean compressed:new boolean[]{false,true}) {
            Path reference=dir.resolve(compressed?"native-compressed-20261025.pvf":"native-uncompressed-20261025.pvf");
            byte[] tail=new byte[1280];try(RandomAccessFile f=new RandomAccessFile(reference.toFile(),"r")){f.seek(f.length()-1280);f.readFully(tail);}
            try(InputStream input=new BufferedInputStream(Files.newInputStream(source));InputStream expected=new BufferedInputStream(Files.newInputStream(reference))) {
                OutputStream compare=new OutputStream() {
                    public void write(int b)throws IOException {assertEquals(b&255,expected.read());}
                    public void write(byte[] b,int off,int len)throws IOException {assertArrayEquals(Arrays.copyOfRange(b,off,off+len),read(expected,len));}
                };
                int mask=compressed?(int)analysis.get("effectiveMask").asLong():0xd6f855e2;
                NativeScriptFileCodec.Result result=NativeScriptFileCodec.encode(input,compare,Files.size(source),sourceHash,mask,compressed,tail);
                assertEquals(-1,expected.read());assertEquals(Files.size(reference),result.artifactBytes);
                assertEquals(compressed?"4f0357a87d1d5dee3a020c1bbd22f37d4614f878c98ab263ceef3ec742f3e515":"e34592496e8d43679bb18beb4287d0c7c50a08b0cff9d088e16993981825db57",result.artifactSha256);
                assertEquals(compressed?"6dc8b73d64fc03a21493bae8ac690a03":"4ce85986e4a3b9c05b4052fff849dea7",result.nativeChecksum);
            }
        }
    }
}
