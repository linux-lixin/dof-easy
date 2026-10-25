package com.aiyi.game.dnfserver.service.dof;

import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Locale;

/** Version-bound Scriptmd5 storage structure, verified by native load 0x840132.
 * The middle field is opaque and must be preserved. This codec does not establish
 * that field's origin, change content parameters, or authorize publication.
 */
public final class NativeScriptMetadata {
    private final String checksum, opaque, filename;

    private NativeScriptMetadata(String checksum, String opaque, String filename) {
        this.checksum=checksum;this.opaque=opaque;this.filename=filename;
    }

    public static NativeScriptMetadata parse(String wire) {
        if (wire==null || wire.length()>4096) throw invalid();
        for (int i=0;i<wire.length();i++) if (Character.isISOControl(wire.charAt(i))) throw invalid();
        try {
            if (Charset.forName("GBK").newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(wire)).remaining()>4096) throw invalid();
        } catch (CharacterCodingException e) {throw invalid();}
        String[] parts=wire.split("\\^\\^",-1);
        if (parts.length!=3 || !(parts[0].equals("0") || parts[0].matches("[a-f0-9]{32}"))) throw invalid();
        // Native resources contain the seed 0^^0^^Script.pvf. Recognize it without
        // treating 0 as a verified checksum. New publish paths must require one.
        validateFilename(parts[2]);
        return new NativeScriptMetadata(parts[0],parts[1],parts[2]);
    }

    private static void validateFilename(String value) {
        String lower=value.toLowerCase(Locale.ROOT);
        if (value.length()<5 || value.length()>128 || !lower.endsWith(".pvf")
            || value.startsWith(".") || value.startsWith(" ") || value.endsWith(" ")
            || value.matches(".*[\\\\/:*?\"<>|].*")) throw invalid();
        String stem=lower.substring(0,lower.indexOf('.'));
        if (stem.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) throw invalid();
    }

    public String checksum() {return checksum;}
    public String filename() {return filename;}
    public boolean hasChecksum() {return checksum.matches("[a-f0-9]{32}");}
    /** Internal storage value only; the opaque part is deliberately not a DTO property. */
    public String encode() {return checksum+"^^"+opaque+"^^"+filename;}
    public NativeScriptMetadata withChecksum(String value) {
        if (value==null || !value.matches("[a-f0-9]{32}")) throw invalid();
        return new NativeScriptMetadata(value,opaque,filename);
    }
    @Override public String toString() {return "NativeScriptMetadata[opaque context preserved; publication unverified]";}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("原生 Script 更新信息格式不兼容，未修改配置");}
}
