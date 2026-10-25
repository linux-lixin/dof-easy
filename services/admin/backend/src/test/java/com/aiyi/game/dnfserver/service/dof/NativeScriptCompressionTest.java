package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeScriptCompressionTest {
    @Test public void realCompressedIndexAndResourcesWhenProvided() throws Exception {
        String originalPath=System.getProperty("qingkong.script.original");
        String nativePath=System.getProperty("qingkong.script.compressed");
        String analysisPath=System.getProperty("qingkong.script.compressedAnalysis");
        org.junit.Assume.assumeNotNull(originalPath,nativePath,analysisPath);
        JsonNode info=new ObjectMapper().readTree(new File(analysisPath));
        long size=info.get("indexBytes").asLong(), compressedSize=info.get("compressedIndexBytes").asLong();
        int crc=(int)info.get("indexCrc").asLong(), mask=(int)info.get("effectiveMask").asLong();
        assertEquals(82583080L,size);assertEquals(38262851L,compressedSize);
        try(InputStream original=new BufferedInputStream(new FileInputStream(originalPath));
            InputStream nativeFile=new BufferedInputStream(new FileInputStream(nativePath))) {
            assertArrayEquals(read(original,56),read(nativeFile,56));
            CountingComparison comparison=new CountingComparison(nativeFile);
            NativeScriptCompression.encodeIndex(original,comparison,size,crc,mask);
            assertEquals(compressedSize,comparison.bytes);
            byte[] buffer=new byte[65536];int n;
            while((n=original.read(buffer))!=-1)comparison.write(buffer,0,n);
            byte[] trailer=read(nativeFile,1280);assertEquals(-1,nativeFile.read());
            for(int i=0;i<1024;i++)assertEquals(0,trailer[i]);
            for(int i=1024;i<1280;i++)assertTrue(trailer[i]>='a' && trailer[i]<='z');
        }
        try(InputStream original=new BufferedInputStream(new FileInputStream(originalPath));
            PushbackInputStream nativeFile=new PushbackInputStream(new BufferedInputStream(new FileInputStream(nativePath)),65536)) {
            assertArrayEquals(read(original,56),read(nativeFile,56));
            CountingComparison comparison=new CountingComparison(original);
            assertEquals(compressedSize,NativeScriptCompression.decodeIndex(nativeFile,comparison,size,crc,mask));
            assertEquals(size,comparison.bytes);
            // Reverse decode must restore the exact resource boundary after read-ahead.
            assertArrayEquals(read(original,65536),read(nativeFile,65536));
        }
    }

    @Test public void fragmentedInputAndResourceBoundary() throws Exception {
        byte[] plain=new byte[131076];new Random(46).nextBytes(plain);
        byte[] wire=encode(plain,0xfedcba98,0x87654321);
        byte[] resources={0,1,2,3,4,5,6};byte[] all=concat(wire,resources);
        for(int chunk:new int[]{1,3,65536}) {
            PushbackInputStream input=new PushbackInputStream(new ByteArrayInputStream(all) {
                @Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(chunk,len));}
            },65536);
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            assertEquals(wire.length,NativeScriptCompression.decodeIndex(input,result,plain.length,0xfedcba98,0x87654321));
            assertArrayEquals(plain,result.toByteArray());assertArrayEquals(resources,read(input,resources.length));assertEquals(-1,input.read());
        }
    }

    @Test public void damagedTruncatedAndWrongLengthStreamsAreRejected() throws Exception {
        byte[] wire=encode(new byte[1024],5,6);
        byte[] damaged=wire.clone();damaged[damaged.length-1]^=1;
        for(byte[] bad:new byte[][]{new byte[0],new byte[]{0x78},Arrays.copyOf(wire,wire.length-1),damaged})
            reject(bad,1024);
        reject(wire,1020);reject(wire,1028); // overflow and short output
        Deflater d=new Deflater();d.setDictionary(new byte[]{1,2,3});
        ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try {DeflaterOutputStream s=new DeflaterOutputStream(packed,d);s.write(new byte[1024]);s.finish();}
        finally {d.end();}
        reject(packed.toByteArray(),1024);
    }

    @Test public void cancellationAndBoundsNeverConsumeInput() throws Exception {
        for(long size:new long[]{0,-4,3,268435460L,Long.MAX_VALUE}) {
            try {NativeScriptCompression.decodeIndex(new PushbackInputStream(new ByteArrayInputStream(new byte[0]),65536),new ByteArrayOutputStream(),size,0,0);fail();}
            catch(IllegalArgumentException expected) { }
        }
        PushbackInputStream input=new PushbackInputStream(new ByteArrayInputStream(new byte[]{7}),65536);
        Thread.currentThread().interrupt();
        try {NativeScriptCompression.decodeIndex(input,new ByteArrayOutputStream(),4,0,0);fail();}
        catch(InterruptedIOException expected) { }
        finally {Thread.interrupted();}
        assertEquals(7,input.read());
    }

    @Test public void excessiveEmptyDeflateBlocksAreBounded() throws Exception {
        ByteArrayOutputStream data=new ByteArrayOutputStream();data.write(new byte[]{0x78,0x01});
        for(int i=0;i<20000;i++)data.write(new byte[]{0,0,0,(byte)255,(byte)255});
        data.write(new byte[]{1,4,0,(byte)251,(byte)255,0,0,0,0,0,4,0,1});
        try {
            NativeScriptCompression.decodeIndex(new PushbackInputStream(new ByteArrayInputStream(data.toByteArray()),65536),new ByteArrayOutputStream(),4,0,0);
            fail("Expected compressed input bound");
        } catch(IOException expected) {assertEquals("Script 压缩索引超过输入长度限制",expected.getMessage());}
    }

    private static void reject(byte[] wire,long length) throws Exception {
        try {NativeScriptCompression.decodeIndex(new PushbackInputStream(new ByteArrayInputStream(wire),65536),new ByteArrayOutputStream(),length,5,6);fail("Expected corrupt stream rejection");}
        catch(IOException expected) { }
    }
    private static byte[] encode(byte[] plain,int crc,int mask)throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        NativeScriptCompression.encodeIndex(new ByteArrayInputStream(plain),output,plain.length,crc,mask);return output.toByteArray();
    }
    private static byte[] concat(byte[] a,byte[] b) {byte[] c=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,c,a.length,b.length);return c;}
    private static byte[] read(InputStream input,int length)throws IOException {byte[] b=new byte[length];new DataInputStream(input).readFully(b);return b;}
    private static final class CountingComparison extends OutputStream {
        private final InputStream expected;private long bytes;
        private CountingComparison(InputStream expected){this.expected=expected;}
        @Override public void write(int b)throws IOException {assertEquals(b&255,expected.read());bytes++;}
        @Override public void write(byte[] b,int off,int len)throws IOException {
            if(!Arrays.equals(Arrays.copyOfRange(b,off,off+len),read(expected,len)))fail("Native bytes differ at stream offset "+bytes);
            bytes+=len;
        }
    }
}
