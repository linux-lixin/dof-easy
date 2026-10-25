package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class NativeScriptIndexTest {
    @Test public void completeRealIndexAndResourceDataWhenProvided() throws Exception {
        String source=System.getProperty("qingkong.script.original"), nativeFile=System.getProperty("qingkong.script.native");
        org.junit.Assume.assumeNotNull(source,nativeFile);
        assertEquals(565213337L,new File(source).length());assertEquals(565214617L,new File(nativeFile).length());
        try (InputStream original=new BufferedInputStream(new FileInputStream(source));
             InputStream expected=new BufferedInputStream(new FileInputStream(nativeFile))) {
            byte[] header=new byte[56], nativeHeader=new byte[56];
            new DataInputStream(original).readFully(header);new DataInputStream(expected).readFully(nativeHeader);
            assertArrayEquals(header,nativeHeader);
            java.nio.ByteBuffer h=java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            assertEquals(36,h.getInt(0));assertEquals(82583080,h.getInt(44));assertEquals(0xd4bd141e,h.getInt(48));
            final DataInputStream reference=new DataInputStream(expected);
            OutputStream comparison=new OutputStream() {
                @Override public void write(int b)throws IOException{assertEquals(b&255,reference.readUnsignedByte());}
                @Override public void write(byte[] b,int off,int len)throws IOException {
                    byte[] actual=new byte[len];reference.readFully(actual);
                    assertArrayEquals(Arrays.copyOfRange(b,off,off+len),actual);
                }
            };
            NativeScriptIndex.transfer(original,comparison,82583080,0xd4bd141e,0xd6f855e2,true);
            byte[] buffer=new byte[65536];int n;
            while((n=original.read(buffer))!=-1)comparison.write(buffer,0,n);
            byte[] suffix=new byte[1280];reference.readFully(suffix);assertEquals(-1,reference.read());
            for(int i=0;i<1024;i++)assertEquals(0,suffix[i]);
            for(int i=1024;i<1280;i++)assertTrue(suffix[i]>='a'&&suffix[i]<='z');
        }
    }
    @Test public void capturedNativeArithmeticVectorsWhenProvided() throws Exception {
        String path=System.getProperty("qingkong.script.nativeVectors");
        org.junit.Assume.assumeNotNull(path);
        com.fasterxml.jackson.databind.JsonNode root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(new File(path));
        assertEquals(18,root.get("vectors").size());
        for(com.fasterxml.jackson.databind.JsonNode v:root.get("vectors")) {
            byte[] original=decodeHex(v.get("originalHex").asText()), expected=decodeHex(v.get("nativeHex").asText());
            byte[] actual=original.clone();int crc=(int)v.get("crc").asLong(),mask=(int)v.get("mask").asLong();
            NativeScriptIndex.transform(actual,0,actual.length,crc,mask,true);assertArrayEquals(expected,actual);
            NativeScriptIndex.transform(actual,0,actual.length,crc,mask,false);assertArrayEquals(original,actual);
        }
    }
    private static byte[] decodeHex(String s) {
        byte[] out=new byte[s.length()/2];
        for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }
    @Test public void nativeSamplePrefixMatchesAndReverses() {
        byte[] original = new byte[]{(byte)0xcf,0x5e,0x13,0x55,(byte)0x8f,(byte)0x92,0x1a,0x55};
        byte[] nativeBytes = new byte[]{(byte)0x89,0x72,(byte)0xf8,(byte)0xd6,(byte)0xb8,0x55,(byte)0xf8,(byte)0xd6};
        byte[] result = original.clone();
        NativeScriptIndex.transform(result,0,result.length,0xd4bd141e,0xd6f855e2,true);
        assertArrayEquals(nativeBytes,result);
        NativeScriptIndex.transform(result,0,result.length,0xd4bd141e,0xd6f855e2,false);
        assertArrayEquals(original,result);
    }
    @Test public void fragmentedInputPreservesResourceBoundaryAndUnalignedArraySlice() throws Exception {
        byte[] all=new byte[131079]; new Random(20261025).nextBytes(all);
        int indexBytes=131072;
        InputStream input=new ByteArrayInputStream(all) {
            @Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(3,len));}
        };
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        NativeScriptIndex.transfer(input,encoded,indexBytes,0xfedcba98,0x89abcdef,true);
        assertEquals(all[indexBytes]&255,input.read());
        byte[] slice=new byte[indexBytes+6];Arrays.fill(slice,(byte)42);
        System.arraycopy(encoded.toByteArray(),0,slice,3,indexBytes);
        NativeScriptIndex.transform(slice,3,indexBytes,0xfedcba98,0x89abcdef,false);
        assertArrayEquals(Arrays.copyOf(all,indexBytes),Arrays.copyOfRange(slice,3,3+indexBytes));
        assertEquals(42,slice[2]);assertEquals(42,slice[indexBytes+3]);
    }
    @Test public void truncationBoundsAndCancellationFailWithoutInventingBytes() throws Exception {
        try {NativeScriptIndex.transfer(new ByteArrayInputStream(new byte[3]),new ByteArrayOutputStream(),4,0,0,true);fail();}
        catch(EOFException expected) { }
        for(long size:new long[]{0,3,-4,Long.MAX_VALUE,256L*1024*1024+4}) {
            try{NativeScriptIndex.transfer(new ByteArrayInputStream(new byte[4]),new ByteArrayOutputStream(),size,0,0,true);fail();}
            catch(IllegalArgumentException expected) { }
        }
        try{NativeScriptIndex.transform(new byte[4],Integer.MAX_VALUE,4,0,0,true);fail();}
        catch(IllegalArgumentException expected) { }
        Thread.currentThread().interrupt();
        try {NativeScriptIndex.transfer(new ByteArrayInputStream(new byte[4]),new ByteArrayOutputStream(),4,0,0,true);fail();}
        catch(InterruptedIOException expected) { }
        finally {Thread.interrupted();}
    }
}
