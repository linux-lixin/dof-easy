package com.aiyi.game.dnfserver.service.dof;

import java.io.*;
import java.util.zip.*;

/** Paired Script index zlib stream; never compresses resource data or publishes files. */
public final class NativeScriptCompression {
    public static final int INPUT_BUFFER_BYTES = 65536;
    private NativeScriptCompression() { }

    /** Read exactly the standard PVF index, transform and emit one level-6 zlib stream. */
    public static void encodeIndex(InputStream input, OutputStream output, long indexBytes,
                                   int crc, int mask) throws IOException {
        validateLength(indexBytes);
        Deflater deflater = new Deflater(6, false);
        try {
            DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater, INPUT_BUFFER_BYTES);
            NativeScriptIndex.transfer(input, stream, indexBytes, crc, mask, true);
            stream.finish(); // Do not close the caller's resource-data stream.
        } finally { deflater.end(); }
    }

    /**
     * Decode exactly one index stream back to standard PVF bytes. The caller must
     * provide at least INPUT_BUFFER_BYTES pushback capacity and continue reading
     * resource data through this SAME stream. Returns compressed bytes consumed.
     * On failure, discard partial output; this method does not publish or retry.
     */
    public static long decodeIndex(PushbackInputStream input, OutputStream output, long indexBytes,
                                   int crc, int mask) throws IOException {
        validateLength(indexBytes);
        Inflater inflater = new Inflater(false);
        byte[] wire = new byte[INPUT_BUFFER_BYTES], plain = new byte[INPUT_BUFFER_BYTES];
        int supplied = 0, pending = 0;
        long decoded = 0;
        // Allow more overhead than a normal zlib stream, but bound streams made
        // of arbitrarily many empty blocks that never reach the output limit.
        long wireLimit = indexBytes + indexBytes / 100 + INPUT_BUFFER_BYTES;
        try {
            while (!inflater.finished()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Script 解压已中断");
                if (inflater.needsInput()) {
                    supplied = input.read(wire);
                    if (supplied < 0) throw new EOFException("Script 压缩索引被截断");
                    if (supplied == 0) {
                        int b = input.read();
                        if (b < 0) throw new EOFException("Script 压缩索引被截断");
                        wire[0] = (byte)b; supplied = 1;
                    }
                    inflater.setInput(wire, 0, supplied);
                }
                int count;
                try { count = inflater.inflate(plain, pending, plain.length - pending); }
                catch (DataFormatException e) { throw new IOException("Script 压缩索引损坏", e); }
                if (inflater.getBytesRead() > wireLimit) throw new IOException("Script 压缩索引超过输入长度限制");
                decoded += count;
                if (decoded > indexBytes) throw new IOException("Script 解压索引超过声明长度");
                int available = pending + count, aligned = available & ~3;
                if (aligned != 0) {
                    NativeScriptIndex.transform(plain, 0, aligned, crc, mask, false);
                    output.write(plain, 0, aligned);
                }
                pending = available - aligned;
                if (pending != 0) System.arraycopy(plain, aligned, plain, 0, pending);
                if (inflater.needsDictionary()) throw new IOException("不支持带字典的 Script 索引");
                if (count == 0 && !inflater.finished() && !inflater.needsInput())
                    throw new IOException("Script 解压没有进展");
            }
            if (decoded != indexBytes || pending != 0) throw new IOException("Script 解压索引长度不一致");
            int unused = inflater.getRemaining();
            if (unused != 0) input.unread(wire, supplied - unused, unused);
            return inflater.getBytesRead();
        } finally { inflater.end(); }
    }

    private static void validateLength(long length) {
        if (length < 4 || length > 256L * 1024 * 1024 || (length & 3) != 0)
            throw new IllegalArgumentException("PVF 索引长度无效");
    }
}
