package com.aiyi.game.dnfserver.test;

import cn.hutool.core.io.FileUtil;
import com.aiyi.game.dnfserver.utils.MinFieldUtil;
import com.aiyi.game.dnfserver.utils.RSATool;
import com.xiaoyouma.dnf.parser.npk.coder.NpkCoder;
import com.xiaoyouma.dnf.parser.npk.model.NpkImg;
import com.xiaoyouma.dnf.parser.npk.model.NpkTexture;

import java.io.File;
import java.util.Base64;

public class Test {

    public static void main(String[] args) {
        NpkCoder.initialize(new File("ImagePacks2").getAbsolutePath());
        NpkImg npkImg = NpkCoder.loadImg("sprite/character/swordman/equipment/avatar/skin/sm_body0000.img");
        System.out.println(npkImg);
        NpkTexture texture = npkImg.getTextures()[209];
        byte[] pngBytes = texture.toPngBytes();
        // 写出
        FileUtil.writeBytes(pngBytes, "/Users/xiatian/projects/GitHub/dnf-server-public/test.png");
    }

    public static void test2(String[] args)  {
        int uid = 18000002;
        String data = String.format("%08x010101010101010101010101010101010101010101010101010101010101010155914510010403030101", uid);
        byte[] dataBytes = hex2tobin(data);
        String private_key = new String(MinFieldUtil.readResource("private.key"))
                .replace("\r", "")
                .replace("\n", "");
        byte[] resultByte = RSATool.encryptByPrivateKey(dataBytes, private_key);
        String s = Base64.getEncoder().encodeToString(resultByte);
//        out.print(strEncode(s, msgkey, rand));
        System.out.println(s);
    }

    /**
     * 将十六进制字符串转换为字节数组
     * 相当于PHP中的hex2tobin函数
     *
     * @param hexStr 十六进制字符串
     * @return 转换后的字节数组
     */
    public static byte[] hex2tobin(String hexStr) {
        if (hexStr == null || hexStr.length() % 2 != 0) {
            throw new IllegalArgumentException("输入的十六进制字符串长度必须是偶数");
        }

        int len = hexStr.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexStr.charAt(i), 16) << 4)
                    + Character.digit(hexStr.charAt(i + 1), 16));
        }

        return data;
    }
}
