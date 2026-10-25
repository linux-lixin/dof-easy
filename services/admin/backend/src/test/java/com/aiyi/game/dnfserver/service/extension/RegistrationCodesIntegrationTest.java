package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.game.dnfserver.DnfServerApplication;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import com.fasterxml.jackson.databind.*;
import org.junit.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in only; the harness supplies a disposable DB. No test endpoint exists in production. */
public class RegistrationCodesIntegrationTest {
 private MockMvc mvc; private DataSource source;private String token;
 private final ObjectMapper json=new ObjectMapper();private final Map<String,Boolean> checks=new LinkedHashMap<>();
 private Map<String,Object> map(Object... values){Map<String,Object>x=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)x.put((String)values[i],values[i+1]);return x;}
 private MvcResult call(String path,String method,Object data,String auth)throws Exception {
  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),"/api/v1/"+path).contentType("application/json").header("Access-Control-Request-Headers","Content-Type");
  if(auth!=null)request.header("Authorization","Bearer "+auth);if(data!=null)request.content(json.writeValueAsBytes(data));return mvc.perform(request).andReturn();
 }
 private JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsByteArray());}
 private int status(MvcResult result){return result.getResponse().getStatus();}
 private void check(String name,boolean ok){checks.put(name,ok);Assert.assertTrue(name,ok);}
 private String query(String sql)throws Exception{try(Connection c=source.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
 private void execute(String sql)throws Exception{try(Connection c=source.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
 private Map<String,Object> payload(String name,String code){String index=UUID.randomUUID().toString();String captcha=UUID.randomUUID().toString();CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE,index),captcha,TimeUnit.MINUTES,5);return map("accountname",name,"password","synthetic-password-only","validationIndex",index,"valicode",captcha,"recommender","activity_admin","uid",99999,"admin",true,"vip","injected","parentUid",99999,"activationCode",code);}
 private JsonNode issue()throws Exception {MvcResult r=call("admin/registration/codes","POST",map("requestId",UUID.randomUUID().toString(),"days",28),token);Assert.assertEquals(200,status(r));return body(r);}
 @Test public void registrationGateUsesOneNativeTransaction()throws Exception {
  Assume.assumeTrue("true".equals(System.getenv("DOF_REGISTRATION_IT")));
  try(ConfigurableApplicationContext context=new SpringApplicationBuilder(DnfServerApplication.class).properties("server.port=0").run()){
   mvc=MockMvcBuilders.webAppContextSetup((WebApplicationContext)context).build();source=context.getBean(DataSource.class);
   MvcResult login=call("login","POST",map("account","activity_admin","password",System.getenv("MYSQL_ROOT_PASSWORD")),null);Assert.assertEquals(login.getResponse().getContentAsString(),200,status(login));token=login.getResponse().getContentAsString();
   String p="admin/registration";JsonNode policy=body(call(p+"/policy","GET",null,token));check("default_off",!policy.get("enabled").asBoolean());
   check("old_protocol_gate_off",status(call("client/register","POST",payload("reg_off",null),null))==200);
   check("privileged_fields_ignored","0::1:0".equals(query("SELECT CONCAT(admin,':',vip,':',parent_uid,':',UID=99999) FROM d_taiwan.accounts WHERE accountname='reg_off'")));
   execute("ALTER TABLE d_taiwan.accounts ADD qcc VARCHAR(64) NOT NULL, ADD login_IP VARCHAR(64) NOT NULL, ADD login_Mac VARCHAR(64) NOT NULL, ADD seal_IP INT NOT NULL, ADD seal_MAC INT NOT NULL, ADD seal_accountname INT NOT NULL");
   Map<String,Object> companion=payload("reg_companion",null);companion.put("valicode",companion.get("valicode").toString().toUpperCase(Locale.ROOT));companion.put("qcc","injected");companion.put("seal_IP",1);
   check("companion_required_columns_and_uppercase_captcha",status(call("client/register","POST",companion,null))==200);
   check("companion_fields_server_owned",":::0:0:0".equals(query("SELECT CONCAT(qcc,':',login_IP,':',login_Mac,':',seal_IP,':',seal_MAC,':',seal_accountname) FROM d_taiwan.accounts WHERE accountname='reg_companion'")));
   Map<String,Object> invalidCaptcha=payload("reg_wrong_captcha",null);invalidCaptcha.put("valicode","wrong");
   check("incorrect_captcha_still_rejected",status(call("client/register","POST",invalidCaptcha,null))==400);
   execute("ALTER TABLE d_taiwan.accounts ADD unknown_required VARCHAR(8) NOT NULL");
   check("unknown_required_column_rejected",status(call("client/register","POST",payload("reg_unknown",null),null))==409);
   check("unknown_column_no_partial_account","0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_unknown'")));
   execute("ALTER TABLE d_taiwan.accounts DROP COLUMN unknown_required");
   Map<String,Object> sameA=payload("reg_same_name",null),sameB=payload("reg_same_name",null);
   ExecutorService samePool=Executors.newFixedThreadPool(2);
   try {
    Future<Integer> a1=samePool.submit(()->status(call("client/register","POST",sameA,null)));
    Future<Integer> b1=samePool.submit(()->status(call("client/register","POST",sameB,null)));
    List<Integer> sameStatuses=Arrays.asList(a1.get(30,TimeUnit.SECONDS),b1.get(30,TimeUnit.SECONDS));Collections.sort(sameStatuses);
    check("gate_off_concurrent_account_once",sameStatuses.equals(Arrays.asList(200,400))&&"1".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_same_name'")));
   } finally {samePool.shutdownNow();}
   MvcResult enabled=call(p+"/policy","PUT",map("enabled",true,"revision",policy.get("revision").asLong()),token);check("enable",status(enabled)==200);policy=body(enabled);
   check("stale_policy_rejected",status(call(p+"/policy","PUT",map("enabled",false,"revision",0),token))==409);
   check("missing_code_rejected",status(call("client/register","POST",payload("reg_missing",null),null))==400);
   Map<String,Object> creation=map("requestId",UUID.randomUUID().toString(),"days",28);JsonNode issued=body(call(p+"/codes","POST",creation,token));String code=issued.get("code").asText();
   check("repeated_generation_rejected",status(call(p+"/codes","POST",creation,token))==409);
   String list=call(p+"/codes","GET",null,token).getResponse().getContentAsString();check("list_has_no_secrets",!list.contains(code)&&!list.contains("code_hash"));
   Map<String,Object> a=payload("reg_parallel_a",code),b=payload("reg_parallel_b",code);
   ExecutorService pool=Executors.newFixedThreadPool(2);try{
    Future<Integer> left=pool.submit(()->status(call("client/register","POST",a,null)));Future<Integer> right=pool.submit(()->status(call("client/register","POST",b,null)));
    List<Integer> statuses=Arrays.asList(left.get(30,TimeUnit.SECONDS),right.get(30,TimeUnit.SECONDS));Collections.sort(statuses);check("concurrent_use_once",statuses.equals(Arrays.asList(200,400)));
   }finally{pool.shutdownNow();}
   check("claim_links_account","1".equals(query("SELECT COUNT(*) FROM dnf_service.REGISTRATION_CODES c JOIN d_taiwan.accounts a ON c.used_uid=a.UID WHERE c.state='used'")));
   check("used_code_cannot_revoke",status(call(p+"/codes/"+issued.get("id").asLong(),"DELETE",null,token))==409);
   JsonNode expired=issue();execute("UPDATE dnf_service.REGISTRATION_CODES SET expires_at=1 WHERE id="+expired.get("id").asLong());check("expired_rejected",status(call("client/register","POST",payload("reg_expired",expired.get("code").asText()),null))==400);
   JsonNode revoked=issue();check("revoke",status(call(p+"/codes/"+revoked.get("id").asLong(),"DELETE",null,token))==200);check("revoked_rejected",status(call("client/register","POST",payload("reg_revoked",revoked.get("code").asText()),null))==400);
   JsonNode rollback=issue();execute("CREATE TRIGGER taiwan_cain_2nd.test_registration_failure BEFORE INSERT ON taiwan_cain_2nd.member_avatar_coin FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
   MvcResult failedInsert=call("client/register","POST",payload("reg_rollback",rollback.get("code").asText()),null);
   check("late_insert_failure_rejected",status(failedInsert)>=400);
   String failureBody=failedInsert.getResponse().getContentAsString();
   check("internal_error_redacted",!failureBody.contains("SQL")&&!failureBody.contains("synthetic failure")&&!failureBody.contains("java.")&&!failureBody.contains("stackTrace"));
   check("account_and_code_rollback","0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_rollback'"))&&"ready".equals(query("SELECT state FROM dnf_service.REGISTRATION_CODES WHERE id="+rollback.get("id").asLong())));
   check("no_orphan_native_rows","0".equals(query("SELECT COUNT(*) FROM d_taiwan.member_info m LEFT JOIN d_taiwan.accounts a ON a.UID=m.m_id WHERE a.UID IS NULL")));
   execute("DROP TRIGGER taiwan_cain_2nd.test_registration_failure");check("retry_after_rollback",status(call("client/register","POST",payload("reg_retried",rollback.get("code").asText()),null))==200);
   JsonNode auditFailure=issue();execute("CREATE TRIGGER dnf_service.test_registration_audit_failure BEFORE INSERT ON dnf_service.REGISTRATION_AUDIT FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure'");
   check("audit_failure_rejected",status(call("client/register","POST",payload("reg_audit_failed",auditFailure.get("code").asText()),null))==503);
   check("audit_failure_rolls_back_account_and_code","0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_audit_failed'"))&&"ready".equals(query("SELECT state FROM dnf_service.REGISTRATION_CODES WHERE id="+auditFailure.get("id").asLong())));
   execute("DROP TRIGGER dnf_service.test_registration_audit_failure");
   execute("ALTER TABLE taiwan_cain_2nd.member_avatar_coin ENGINE=MyISAM");check("nontransaction_engine_rejected",status(call("client/register","POST",payload("reg_bad_engine",code),null))==409);
   check("disable_allowed_on_unsupported_engine",status(call(p+"/policy","PUT",map("enabled",false,"revision",policy.get("revision").asLong()),token))==200);
   check("gate_off_nontransaction_registration_rejected",status(call("client/register","POST",payload("reg_off_bad_engine",null),null))==409);
   execute("ALTER TABLE taiwan_cain_2nd.member_avatar_coin ENGINE=InnoDB");check("disable_preserves_codes","5".equals(query("SELECT COUNT(*) FROM dnf_service.REGISTRATION_CODES")));
   String audit=call(p+"/history","GET",null,token).getResponse().getContentAsString();check("audit_has_no_secrets",!audit.contains(code)&&json.readTree(audit).size()>=8);
  }finally{System.out.println("REGISTRATION_CHECKS:"+json.writeValueAsString(checks));}
 }
}
