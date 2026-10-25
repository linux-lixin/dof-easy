package com.aiyi.game.dnfserver.service.dof;
import java.io.*;
import java.nio.file.*;
import org.junit.Assume;
/** Captured vectors and snapshots are supplied locally, never committed. */
final class PrivateNativeFixtures {
    static InputStream open(String name)throws IOException {
        String root=System.getProperty("qingkong.private.fixtures", "");
        Assume.assumeTrue("requires private native comparison inputs",!root.isEmpty());
        Path p=Paths.get(root).resolve(name);
        Assume.assumeTrue("private input not supplied",Files.isRegularFile(p));
        return Files.newInputStream(p);
    }
}
