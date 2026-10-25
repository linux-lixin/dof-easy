package com.aiyi.game.dnfserver.service.dof;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Streaming paired-format candidate encoder, not a publisher or PVF semantic validator.
 * The caller supplies the verified numeric mask and owns both streams. On any failure
 * the partial output MUST be discarded. No parameter defaults or identity are invented.
 */
public final class NativeScriptFileCodec {
    private static final long MAX_BYTES = Integer.MAX_VALUE;
    private NativeScriptFileCodec() { }

    public static final class Result {
        public final String sourceSha256, artifactSha256, nativeChecksum;
        public final long sourceBytes, artifactBytes;
        public final boolean compressed;
        private Result(String sourceSha, String artifactSha, String checksum, long source, long artifact, boolean packed) {
            sourceSha256=sourceSha; artifactSha256=artifactSha; nativeChecksum=checksum;
            sourceBytes=source; artifactBytes=artifact; compressed=packed;
        }
    }
    public static byte[] newTrailer(SecureRandom random) {
        if(random==null) throw new IllegalArgumentException("缺少随机源");
        byte[] tail=new byte[1280];
        for(int i=1024;i<tail.length;i++) tail[i]=(byte)('a'+random.nextInt(26));
        return tail;
    }
    private static MessageDigest digest(String algorithm) {
        try {return MessageDigest.getInstance(algorithm);} catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static String hex(byte[] bytes) {
        StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format("%02x",b&255));return text.toString();
    }
    private static void active() throws InterruptedIOException {
        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Script 文件处理已中断");
    }
    public static Result encode(InputStream source, OutputStream target, long sourceBytes,
            String expectedSourceSha256, int mask, boolean compressed, byte[] trailer) throws IOException {
        if(source==null || target==null || sourceBytes<60 || sourceBytes>MAX_BYTES-1280
                || expectedSourceSha256==null || !expectedSourceSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Script 输入长度或来源校验无效");
        if(trailer==null || trailer.length!=1280) throw new IllegalArgumentException("Script 尾部格式无效");
        byte[] tail=trailer.clone();
        for(int i=0;i<1024;i++)if(tail[i]!=0)throw new IllegalArgumentException("Script 尾部格式无效");
        for(int i=1024;i<tail.length;i++)if(tail[i]<'a'||tail[i]>'z')throw new IllegalArgumentException("Script 尾部格式无效");
        active();
        MessageDigest original=digest("SHA-256"),artifact=digest("SHA-256");
        DigestInputStream input=new DigestInputStream(source,original);
        final long[] written={0};
        OutputStream output=new OutputStream() {
            @Override public void write(int b)throws IOException {write(new byte[]{(byte)b},0,1);}
            @Override public void write(byte[] b,int off,int len)throws IOException {
                active(); if(len>MAX_BYTES-written[0])throw new IOException("Script 产物超过客户端长度限制");
                target.write(b,off,len);artifact.update(b,off,len);written[0]+=len;
            }
        };
        byte[] header=new byte[56];new DataInputStream(input).readFully(header);
        ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        String guid=new String(header,4,36,StandardCharsets.US_ASCII);
        long indexBytes=Integer.toUnsignedLong(h.getInt(44)),fileCount=Integer.toUnsignedLong(h.getInt(52));
        if(h.getInt(0)!=36 || !guid.matches("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
                || h.getInt(40)!=0xf851 || indexBytes<4 || indexBytes>256L*1024*1024
                || (indexBytes&3)!=0 || indexBytes>sourceBytes-56 || fileCount>indexBytes/20)
            throw new IOException("Script 头部与当前配套格式不符");
        output.write(header);
        if(compressed)NativeScriptCompression.encodeIndex(input,output,indexBytes,h.getInt(48),mask);
        else NativeScriptIndex.transfer(input,output,indexBytes,h.getInt(48),mask,true);
        long remaining=sourceBytes-56-indexBytes;byte[] buffer=new byte[65536];
        while(remaining>0) {
            active();int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));
            if(n<0)throw new EOFException("Script 资源数据被截断");
            if(n==0) {int one=input.read();if(one<0)throw new EOFException("Script 资源数据被截断");buffer[0]=(byte)one;n=1;}
            output.write(buffer,0,n);remaining-=n;
        }
        if(input.read()!=-1)throw new IOException("Script 输入长度在处理期间发生变化");
        String sourceSha=hex(original.digest());
        if(!sourceSha.equals(expectedSourceSha256))throw new IOException("Script 来源校验不一致，丢弃候选产物");
        output.write(tail);
        MessageDigest nativeDigest=digest("MD5");nativeDigest.update(tail,1180,100);
        return new Result(sourceSha,hex(artifact.digest()),hex(nativeDigest.digest()),sourceBytes,written[0],compressed);
    }
}
