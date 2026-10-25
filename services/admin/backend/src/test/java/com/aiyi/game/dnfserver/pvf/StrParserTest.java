package com.aiyi.game.dnfserver.pvf;
import com.xiaoyouma.dnf.parser.pvf.parser.StrParser;
import cn.hutool.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class StrParserTest {
 @Test public void pairedTextHandlesMalformedLinesWithoutLosingValues() {
  StrParser.resetDiagnostics();
  JSONObject v=new StrParser().parse("\uFEFF// comment\r\nkey>name>suffix\nempty>\nmalformed\rkey>different\n");
  assertEquals("name>suffix",v.getStr("key"));assertEquals("",v.getStr("empty"));
  assertEquals(2,v.size());assertEquals(1,StrParser.invalidLines());assertEquals(1,StrParser.duplicateKeys());
 }
}
