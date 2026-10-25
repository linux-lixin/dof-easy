package com.aiyi.game.dnfserver.service.extension;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReleaseVersionTest {
 @Test public void numericOrdering() {
  assertTrue(ReleaseVersion.compare("1.10","1.9")>0);
  assertEquals(0,ReleaseVersion.compare("01.0.0","1"));
  assertTrue(ReleaseVersion.compare("1.999999999999999999999","1.9")>0);
  assertFalse(ReleaseVersion.valid("1.2-preview"));
  assertFalse(ReleaseVersion.valid("1..2"));
 }
}
