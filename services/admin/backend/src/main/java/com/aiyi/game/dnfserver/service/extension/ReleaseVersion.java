package com.aiyi.game.dnfserver.service.extension;

import java.math.BigInteger;

/** Numeric dotted release versions; never compare update order using SQL text collation. */
public final class ReleaseVersion {
    private ReleaseVersion() {}
    public static boolean valid(String value) {
        return value != null && value.length() <= 64 && value.matches("[0-9]+(\\.[0-9]+)*");
    }
    public static void require(String value) {
        if (!valid(value)) throw new ActivityConfigService.Failure(400, "版本号须为点分数字，例如 1.10.0");
    }
    public static int compare(String a, String b) {
        require(a); require(b);
        String[] x=a.split("\\."), y=b.split("\\.");
        for (int i=0;i<Math.max(x.length,y.length);i++) {
            int c=new BigInteger(i<x.length?x[i]:"0").compareTo(new BigInteger(i<y.length?y[i]:"0"));
            if (c!=0) return c;
        }
        return 0;
    }
}
