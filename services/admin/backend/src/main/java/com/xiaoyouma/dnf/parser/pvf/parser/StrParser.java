package com.xiaoyouma.dnf.parser.pvf.parser;

import cn.hutool.json.JSONObject;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility replacement for dnf-parser-pvf 1.1's non-extensible STR parser.
 * Application classes precede dependency jars in the Spring Boot class loader.
 * Parses key>value text without changing or writing any PVF bytes. Invalid lines
 * and duplicate keys are counted: a partial name dictionary is never a clean audit.
 */
public class StrParser implements IParser {
    private static final AtomicInteger invalid = new AtomicInteger();
    private static final AtomicInteger duplicates = new AtomicInteger();
    public static int invalidLines() { return invalid.get(); }
    public static int duplicateKeys() { return duplicates.get(); }
    public static void resetDiagnostics() { invalid.set(0); duplicates.set(0); }
    public JSONObject parse(String text) {
        JSONObject result = new JSONObject();
        for (String line : text.replace("\uFEFF", "").split("\\r\\n|\\n|\\r", -1)) {
            if (line.trim().isEmpty() || line.trim().startsWith("//")) continue;
            int separator = line.indexOf('>');
            if (separator <= 0) { invalid.incrementAndGet(); continue; }
            String key = line.substring(0, separator);
            if (result.containsKey(key)) { duplicates.incrementAndGet(); continue; }
            result.set(key, line.substring(separator + 1));
        }
        return result;
    }
    @Override public JSONObject convert(Pvf pvf, byte[] bytes) {
        return parse(new String(bytes, pvf.getCharset()));
    }
    @Override public String convertSource(Pvf pvf, byte[] bytes) {
        return new String(bytes, pvf.getCharset());
    }
}
