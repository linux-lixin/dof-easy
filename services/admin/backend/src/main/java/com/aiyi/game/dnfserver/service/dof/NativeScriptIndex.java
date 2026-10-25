package com.aiyi.game.dnfserver.service.dof;

import java.io.*;

/**
 * Index primitives verified against paired tool functions 0x8e25ac/0x8e262c.
 * This is not a publisher: key-parameter derivation, compressed mode, trailer
 * generation and native update consumption require their own verification.
 */
public final class NativeScriptIndex {
    private static final int PVF_MASK = 0x81a79011;
    private NativeScriptIndex() { }

    public static void transform(byte[] block, int offset, int length, int crc, int mask, boolean encrypt) {
        if (block == null || offset < 0 || length < 0 || (length & 3) != 0
            || offset > block.length - length) throw new IllegalArgumentException("PVF 索引块边界无效");
        for (int i = offset; i < offset + length; i += 4) {
            int word = (block[i] & 255) | (block[i+1] & 255) << 8
                | (block[i+2] & 255) << 16 | block[i+3] << 24;
            word = encrypt ? Integer.rotateRight(word ^ crc ^ PVF_MASK, 6) ^ mask
                : Integer.rotateLeft(word ^ mask, 6) ^ crc ^ PVF_MASK;
            block[i] = (byte)word; block[i+1] = (byte)(word >>> 8);
            block[i+2] = (byte)(word >>> 16); block[i+3] = (byte)(word >>> 24);
        }
    }

    /** Consume precisely indexBytes, leaving the subsequent resource data unread. */
    public static void transfer(InputStream input, OutputStream output, long indexBytes,
                                int crc, int mask, boolean encrypt) throws IOException {
        if (indexBytes < 4 || indexBytes > 256L * 1024 * 1024 || (indexBytes & 3) != 0)
            throw new IllegalArgumentException("PVF 索引长度无效");
        byte[] buffer = new byte[64 * 1024];
        long remaining = indexBytes;
        while (remaining != 0) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("PVF 处理已中断");
            int count = (int)Math.min(buffer.length, remaining), position = 0;
            while (position < count) {
                int n = input.read(buffer, position, count-position);
                if (n < 0) throw new EOFException("PVF 索引被截断");
                if (n == 0) {
                    int value = input.read();
                    if (value < 0) throw new EOFException("PVF 索引被截断");
                    buffer[position++] = (byte)value;
                } else position += n;
            }
            transform(buffer, 0, count, crc, mask, encrypt);
            output.write(buffer, 0, count); remaining -= count;
        }
    }
}
