package com.aiyi.game.dnfserver.service.dof;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Dof 2.8 updater compatibility digest: MD5 of the final 100 bytes.
 * It does not cover the file body. Publication must also verify the complete
 * artifact SHA-256 and format; this class does not publish or modify metadata.
 */
public final class NativeScriptChecksum {
    public static final int WINDOW_BYTES=100;
    private NativeScriptChecksum() { }

    public static String calculate(Path artifact) throws IOException {
        BasicFileAttributes attributes=Files.readAttributes(artifact,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new IOException("Script 校验对象必须是普通文件");
        try (FileChannel input=FileChannel.open(artifact,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            long size=input.size();
            // Native file-length command uses a signed 32-bit value. Small files
            // are never valid encrypted PVFs; do not inherit its failed-seek fallback.
            if (size<WINDOW_BYTES || size>Integer.MAX_VALUE) throw new IOException("Script 文件长度不在已验证的原生校验范围");
            ByteBuffer tail=ByteBuffer.allocate(WINDOW_BYTES);long position=size-WINDOW_BYTES;
            while (tail.hasRemaining()) {
                int read=input.read(tail,position);
                if (read<0) throw new EOFException("Script 校验期间文件被截断");
                if (read==0) throw new IOException("Script 尾部读取未取得进展");
                position+=read;
            }
            if (input.size()!=size) throw new IOException("Script 校验期间文件长度发生变化");
            return digest(tail.array());
        }
    }

    static String digest(byte[] tail) {
        if (tail==null || tail.length!=WINDOW_BYTES) throw new IllegalArgumentException("原生 Script 摘要必须使用末尾 100 字节");
        try {
            byte[] digest=MessageDigest.getInstance("MD5").digest(tail);StringBuilder hex=new StringBuilder(32);
            for (byte b:digest) hex.append(String.format("%02x",b&255));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }

    public static boolean matches(Path artifact,String expected) throws IOException {
        if (expected==null || !expected.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("原生 Script 摘要格式无效");
        return calculate(artifact).equals(expected);
    }
}
