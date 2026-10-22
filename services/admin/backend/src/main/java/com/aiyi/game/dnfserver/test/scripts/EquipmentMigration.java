package com.aiyi.game.dnfserver.test.scripts;

import com.github.houbb.heaven.util.io.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class EquipmentMigration {

    public static void main(String[] args) throws UnsupportedEncodingException {
        // 将牛服的装备命名与国服对齐, 先读取国服装备数据到Map, 再遍历牛服数据进行更新.
        String cattleStrPath = "/Users/xiatian/Desktop/1/equipment.kor.new.str";
        String cnStrPath = "/Users/xiatian/Desktop/1/equipment.kor.str";
        Map<String, String> cnStrMap = buildCnStrMap(cnStrPath);
        String cattleStr = readCnStr(cattleStrPath);
        StringBuilder updatedCattleStr = new StringBuilder();
        String[] lines = cattleStr.split("\n");
        for (String line : lines) {
            if (null == line || line.trim().isEmpty()) {
                updatedCattleStr.append("\r\n");
                continue;
            }
            line = line.replace("\r", "");
            // 格式key>value, value可能包含'>', 只分割第一个
            int sepIndex = line.indexOf('>');
            if (sepIndex != -1) {
                String key = line.substring(0, sepIndex).trim();
                if (cnStrMap.containsKey(key)) {
                    String newValue = cnStrMap.get(key);
                    if (newValue.startsWith("name")){
                        updatedCattleStr.append(line);
                    }else{
                        updatedCattleStr.append(key).append(">").append(newValue);
                    }
                } else {
                    updatedCattleStr.append(line);
                }
            } else {
                updatedCattleStr.append(line);
            }
            updatedCattleStr.append("\r\n");
        }
        FileUtil.write("/Users/xiatian/Desktop/1/equipment.kor.updated.str",
                updatedCattleStr.toString().getBytes("Big5"));
        System.out.println("更新完成");
    }

    private static Map<String, String> buildCnStrMap(String cnStrPath) {
        Map<String, String> cnStrMap = new HashMap<String, String>();
        String cnStr = readCnStr(cnStrPath);
        String[] lines = cnStr.split("\n");
        for (String line : lines) {
            if (null == line || line.trim().isEmpty()) {
                continue;
            }
            // 格式key>value, value可能包含'>', 只分割第一个
            int sepIndex = line.indexOf('>');
            if (sepIndex != -1) {
                String key = line.substring(0, sepIndex).trim();
                String value = line.substring(sepIndex + 1).trim();
                cnStrMap.put(key, value);
            }
        }
        return cnStrMap;
    }

    private static String readCnStr(String cnStrPath) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (FileInputStream in = new FileInputStream(cnStrPath)){
            for (int len = in.read(buffer); len != -1; len = in.read(buffer)) {
                out.write(buffer, 0, len);
            }
            return out.toString("Big5");
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }


}
