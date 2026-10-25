package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Uncompressed paired format only. 0 is the native uncompressed marker, not
 * a general compression boolean. Compressed marker derivation is unsupported.
 * A fresh decimal index mask is chosen; the native tool's time-based random
 * selection is deliberately not reproduced or claimed as cryptographic secrecy.
 */
public final class NativeScriptParameters {
    private final int number;
    private NativeScriptParameters(int number) {
        if(number<0 || number>99999999)throw new IllegalArgumentException("不压缩 Script 参数超出已支持范围");
        this.number=number;
    }
    public static NativeScriptParameters create(SecureRandom random) {
        if(random==null)throw new IllegalArgumentException("缺少随机源");
        return new NativeScriptParameters(random.nextInt(99999999)+1);
    }
    public static NativeScriptParameters decode(String envelope,String clientSha) {
        String plain=new String(NativeScriptContext.decode(envelope,clientSha),StandardCharsets.US_ASCII);
        if(!plain.matches("0##(?:0|[1-9][0-9]{0,7})"))throw new IllegalArgumentException("仅支持已核对的不压缩 Script 参数格式");
        return new NativeScriptParameters(Integer.parseInt(plain.substring(3)));
    }
    public int effectiveMask(int indexCrc) {return number ^ indexCrc;}
    public String envelope(String clientSha) {
        return NativeScriptContext.encode(("0##"+number).getBytes(StandardCharsets.US_ASCII),clientSha);
    }
    @Override public String toString() {return "NativeScriptParameters[uncompressed, private]";}
}
