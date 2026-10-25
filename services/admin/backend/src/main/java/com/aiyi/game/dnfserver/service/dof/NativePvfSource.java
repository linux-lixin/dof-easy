package com.aiyi.game.dnfserver.service.dof;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Bounded, streaming validation of the original PVF index. Does not load game scripts.
 * Header and index-entry layout agree with the paired source and existing Pvf parser.
 * Rejects Qingkong-encrypted input instead of silently encrypting it a second time.
 */
public final class NativePvfSource {
    public static final long MAX_BYTES = Integer.MAX_VALUE - 1280L;
    public static final class Header {
        public final long bytes, indexBytes, fileCount;
        public final int indexCrc;
        Header(long bytes, long indexBytes, long fileCount, int indexCrc) {
            this.bytes=bytes; this.indexBytes=indexBytes; this.fileCount=fileCount; this.indexCrc=indexCrc;
        }
    }
    private NativePvfSource() { }
    public static Header header(byte[] raw, long bytes) throws IOException {
        if(raw==null || raw.length!=56 || bytes<80 || bytes>MAX_BYTES) throw invalid();
        ByteBuffer h=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        long index=Integer.toUnsignedLong(h.getInt(44)),count=Integer.toUnsignedLong(h.getInt(52));
        String guid=new String(raw,4,36,StandardCharsets.US_ASCII);
        if(h.getInt()!=36 || !guid.matches("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
            || h.getInt(40)!=0xf851 || index<24 || index>256L*1024*1024 || (index&3)!=0
            || index>bytes-56 || count<1 || count>index/21) throw invalid();
        return new Header(bytes,index,count,h.getInt(48));
    }
    public static Header validate(InputStream source, long bytes) throws IOException {
        DataInputStream in=new DataInputStream(new BufferedInputStream(source,65536));
        byte[] raw=new byte[56]; in.readFully(raw); Header h=header(raw,bytes);
        Index index=new Index(in,h.indexBytes,h.indexCrc);
        long resources=bytes-56-h.indexBytes;
        for(long i=0;i<h.fileCount;i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("PVF 检查已中断");
            index.number(); long pathBytes=index.number();
            if(pathBytes<1 || pathBytes>4096 || pathBytes>index.remaining()-12)throw invalid();
            for(long p=0;p<pathBytes;p++) if(index.next()==0)throw invalid();
            long length=index.number(); index.number(); long offset=index.number();
            long padded=(length+3)&~3L;
            if(offset>resources || padded>resources-offset)throw invalid();
        }
        if(index.remaining()>3)throw invalid();
        while(index.remaining()>0)if(index.next()!=0)throw invalid();
        byte[] buffer=new byte[65536]; long left=resources;
        while(left>0) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("PVF 检查已中断");
            int n=in.read(buffer,0,(int)Math.min(buffer.length,left));
            if(n<0)throw new EOFException("PVF 资源区被截断");
            left-=n;
        }
        if(in.read()!=-1)throw new IOException("PVF 长度在检查期间发生变化");
        return h;
    }
    private static IOException invalid() {return new IOException("PVF 不是当前配套的原始格式，或索引、资源边界损坏；请勿上传已加密文件");}
    private static final class Index {
        private final DataInputStream in;
        private final int crc;
        private long left;
        private int word, available;
        Index(DataInputStream in,long left,int crc){this.in=in;this.left=left;this.crc=crc;}
        long remaining(){return left;}
        int next()throws IOException {
            if(left==0)throw invalid();
            if(available==0) {
                word=Integer.rotateRight(Integer.reverseBytes(in.readInt())^crc^0x81a79011,6);available=4;
            }
            int b=word&255;word>>>=8;available--;left--;return b;
        }
        long number()throws IOException {
            long n=0;for(int i=0;i<4;i++)n|=(long)next()<<(8*i);return n;
        }
    }
}
