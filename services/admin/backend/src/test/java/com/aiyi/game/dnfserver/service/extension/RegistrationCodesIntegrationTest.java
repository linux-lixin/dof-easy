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
 private MvcResult fromIp(Map<String,Object> data,String ip)throws Exception{return mvc.perform(MockMvcRequestBuilders.post("/api/v1/client/register").contentType("application/json").header("Access-Control-Request-Headers","Content-Type").content(json.writeValueAsBytes(data)).with(r->{r.setRemoteAddr(ip);return r;})).andReturn();}
 private JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsByteArray());}
 private int status(MvcResult result){return result.getResponse().getStatus();}
 private void check(String name,boolean ok){checks.put(name,ok);if(!ok&&source!=null)try(Connection c=source.getConnection();Statement st=c.createStatement();ResultSet r=st.executeQuery("SHOW ENGINE INNODB STATUS")){if(r.next())java.nio.file.Files.write(java.nio.file.Paths.get("innodb-failure.txt"),r.getString(3).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}Assert.assertTrue(name,ok);}
 private String query(String sql)throws Exception{try(Connection c=source.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
 private void execute(String sql)throws Exception{try(Connection c=source.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
 private Map<String,Object> payload(String name,String code){String index=UUID.randomUUID().toString();String captcha=UUID.randomUUID().toString().substring(0,8);CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE,index),captcha,TimeUnit.MINUTES,5);return map("accountname",name,"password","synthetic-password-only","validationIndex",index,"valicode",captcha,"recommender","activity_admin","uid",99999,"admin",true,"vip","injected","parentUid",99999,"activationCode",code);}
 private Map<String,Object> edit(String op,String revision){return map("requestId",UUID.randomUUID().toString(),"operation",op,"revision",revision,"reason","disposable registration integration");}
 private MvcResult save(String path,String method,Map<String,Object> edit)throws Exception {MvcResult preview=call("admin/registration/preview","POST",edit,token);if(status(preview)!=200)return preview;return call(path,method,edit,token);}
 private JsonNode issue()throws Exception {JsonNode policy=body(call("admin/registration/policy","GET",null,token));Map<String,Object> e=edit("create",policy.get("codeRevision").asText());e.put("days",28);MvcResult r=save("admin/registration/codes","POST",e);Assert.assertEquals(r.getResponse().getContentAsString(),200,status(r));return body(r);}
 private MvcResult revoke(long id)throws Exception {JsonNode state=body(call("admin/registration/codes/"+id,"GET",null,token));Map<String,Object> e=edit("revoke",state.path("revision").asText());e.put("codeId",id);return save("admin/registration/codes/"+id,"DELETE",e);}
 private MvcResult policy(boolean value)throws Exception {JsonNode state=body(call("admin/registration/policy","GET",null,token));Map<String,Object> e=edit("policy",state.get("revision").asText());e.put("enabled",value);return save("admin/registration/policy","PUT",e);}
 @Test public void registrationGateUsesOneNativeTransaction()throws Exception {
  Assume.assumeTrue("true".equals(System.getenv("DOF_REGISTRATION_IT")));
  try(ConfigurableApplicationContext context=new SpringApplicationBuilder(DnfServerApplication.class).run("--server.port=0","--server.address=127.0.0.1")){
   mvc=MockMvcBuilders.webAppContextSetup((WebApplicationContext)context).build();source=context.getBean(DataSource.class);
   try(Connection c=source.getConnection();Statement st=c.createStatement();ResultSet guard=st.executeQuery("SELECT @@server_uuid,purpose FROM dnf_service.DOF_CONTRACT_TEST_MARKER")){Assert.assertTrue(guard.next());Assert.assertEquals(System.getenv("DOF_DATABASE_UUID"),guard.getString(1));Assert.assertEquals("disposable-registration",guard.getString(2));Assert.assertFalse(guard.next());}
   MvcResult login=call("login","POST",map("account","activity_admin","password",System.getenv("MYSQL_ROOT_PASSWORD")),null);Assert.assertEquals(login.getResponse().getContentAsString(),200,status(login));token=login.getResponse().getContentAsString();
   String p="admin/registration";JsonNode policy=body(call(p+"/policy","GET",null,token));check("default_off",!policy.get("enabled").asBoolean());
   check("old_protocol_gate_off",status(call("client/register","POST",payload("reg_off",null),null))==200);
   check("zero_gift_does_not_grant_hardcoded_points","0".equals(query("SELECT cera FROM taiwan_billing.cash_cera JOIN d_taiwan.accounts ON account=UID WHERE accountname='reg_off'")));
   check("privileged_fields_ignored","0::1:0".equals(query("SELECT CONCAT(admin,':',vip,':',parent_uid,':',UID=99999) FROM d_taiwan.accounts WHERE accountname='reg_off'")));
   execute("ALTER TABLE d_taiwan.accounts ADD qcc VARCHAR(64) NOT NULL, ADD login_IP VARCHAR(64) NOT NULL, ADD login_Mac VARCHAR(64) NOT NULL, ADD seal_IP INT NOT NULL, ADD seal_MAC INT NOT NULL, ADD seal_accountname INT NOT NULL");
   Map<String,Object> companion=payload("reg_companion",null);companion.put("valicode",companion.get("valicode").toString().toUpperCase(Locale.ROOT));companion.put("qcc","injected");companion.put("seal_IP",1);
   check("companion_required_columns_and_uppercase_captcha",status(call("client/register","POST",companion,null))==200);
   check("companion_fields_server_owned",":::0:0:0".equals(query("SELECT CONCAT(qcc,':',login_IP,':',login_Mac,':',seal_IP,':',seal_MAC,':',seal_accountname) FROM d_taiwan.accounts WHERE accountname='reg_companion'")));
   Map<String,Object> replay=payload("reg_receipt",null);replay.put("registrationRequestId",UUID.randomUUID().toString());
   execute("UPDATE dof_native.billing SET regnumber=37,regtype=1");
   check("receipt_registration",status(call("client/register","POST",replay,null))==200);
   check("configured_points_once","37".equals(query("SELECT cera FROM taiwan_billing.cash_cera JOIN d_taiwan.accounts ON account=UID WHERE accountname='reg_receipt'")));
   execute("UPDATE taiwan_billing.cash_cera JOIN d_taiwan.accounts ON account=UID SET cera=41 WHERE accountname='reg_receipt'");
   execute("UPDATE d_taiwan.accounts SET password='later-password-hash' WHERE accountname='reg_receipt'");
   check("completed_replay_bypasses_consumed_captcha",status(call("client/register","POST",replay,null))==200);
   check("replay_keeps_later_progress","41:later-password-hash".equals(query("SELECT CONCAT(cera,':',password) FROM taiwan_billing.cash_cera JOIN d_taiwan.accounts ON account=UID WHERE accountname='reg_receipt'")));
   Map<String,Object> changed=new LinkedHashMap<>(replay);changed.put("password","changed");check("same_nonce_different_payload_rejected",status(call("client/register","POST",changed,null))==409);
   execute("UPDATE dof_native.billing SET regnumber=23,regtype=0");
   check("configured_tokens",status(call("client/register","POST",payload("reg_tokens",null),null))==200&&"23".equals(query("SELECT cera_point FROM taiwan_billing.cash_cera_point JOIN d_taiwan.accounts ON account=UID WHERE accountname='reg_tokens'")));
   execute("UPDATE dof_native.billing SET ip_reg_control=0");check("registration_closed",status(call("client/register","POST",payload("reg_closed",null),null))==403);
   execute("UPDATE dof_native.billing SET ip_reg_control=1");check("ip_quota_applies",status(call("client/register","POST",payload("reg_quota",null),null))==403);
   execute("UPDATE d_taiwan.accounts SET ip='changed-after-login' WHERE admin=0");
   check("quota_uses_immutable_receipt_not_mutable_login_ip",status(call("client/register","POST",payload("reg_ip_changed",null),null))==403);
   Map<String,Object> ipA=payload("reg_ip_a",null),ipB=payload("reg_ip_b",null);ipA.put("registrationClientIp","192.0.2.99");ipB.put("registrationClientIp","192.0.2.99");
   ExecutorService ipPool=Executors.newFixedThreadPool(2);try{Future<Integer> ia=ipPool.submit(()->status(fromIp(ipA,"203.0.113.20"))),ib=ipPool.submit(()->status(fromIp(ipB,"203.0.113.20")));List<Integer> values=Arrays.asList(ia.get(30,TimeUnit.SECONDS),ib.get(30,TimeUnit.SECONDS));Collections.sort(values);check("positive_ip_quota_concurrent_once_"+values,values.equals(Arrays.asList(200,403)));}finally{ipPool.shutdownNow();}
   check("untrusted_body_ip_ignored","1".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname IN ('reg_ip_a','reg_ip_b') AND ip='203.0.113.20'")));
   execute("INSERT INTO d_taiwan.accounts(accountname,password,qq,ip,VIP,admin,parent_uid,qcc,login_IP,login_Mac,seal_IP,seal_MAC,seal_accountname) VALUES('fixture_external','synthetic-external-hash','','203.0.113.99','',0,1,'','','',0,0,0)");
   check("unverified_other_registration_source_refused",status(fromIp(payload("reg_unverified_origin",null),"203.0.113.40"))==409&&!body(call("admin/registration/policy","GET",null,token)).get("registrationReadiness").get("ready").asBoolean());
   execute("DELETE FROM d_taiwan.accounts WHERE accountname='fixture_external'");
   check("explicit_replay_from_new_ip_keeps_account",status(fromIp(replay,"203.0.113.30"))==200);
   execute("UPDATE dof_native.billing SET ip_reg_control=-1,regnumber=0");
   execute("UPDATE dof_native.config SET switch_regCdkey=1");check("native_activation_gate_cannot_be_bypassed_by_own_protocol",status(call("client/register","POST",payload("reg_native_gate",null),null))==409&&"0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_native_gate'")));execute("UPDATE dof_native.config SET switch_regCdkey=0");
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
    check("gate_off_concurrent_account_once",(sameStatuses.equals(Arrays.asList(200,400))||sameStatuses.equals(Arrays.asList(200,503)))&&"1".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_same_name'")));
   } finally {samePool.shutdownNow();}
   MvcResult enabled=policy(true);check("enable",status(enabled)==200);policy=body(enabled);
   check("missing_write_protocol_rejected",status(call(p+"/policy","PUT",map("enabled",false,"revision",0),token))==400);
   check("missing_code_rejected",status(call("client/register","POST",payload("reg_missing",null),null))==400);
   Map<String,Object> creation=edit("create",body(call(p+"/policy","GET",null,token)).get("codeRevision").asText());creation.put("days",28);JsonNode issued=body(save(p+"/codes","POST",creation));String code=issued.get("code").asText();
   check("repeated_generation_same_code",code.equals(body(call(p+"/codes","POST",creation,token)).get("code").asText()));
   String list=call(p+"/codes","GET",null,token).getResponse().getContentAsString();check("list_has_no_secrets",!list.contains(code)&&!list.contains("code_hash"));
   Map<String,Object> a=payload("reg_parallel_a",code),b=payload("reg_parallel_b",code);
   ExecutorService pool=Executors.newFixedThreadPool(2);try{
    Future<Integer> left=pool.submit(()->status(call("client/register","POST",a,null)));Future<Integer> right=pool.submit(()->status(call("client/register","POST",b,null)));
    List<Integer> statuses=Arrays.asList(left.get(30,TimeUnit.SECONDS),right.get(30,TimeUnit.SECONDS));Collections.sort(statuses);check("concurrent_use_once_"+statuses,statuses.equals(Arrays.asList(200,400)));
   }finally{pool.shutdownNow();}
   check("claim_links_account","1".equals(query("SELECT COUNT(*) FROM dnf_service.REGISTRATION_CODES c JOIN d_taiwan.accounts a ON c.used_uid=a.UID WHERE c.state='used'")));
   check("used_code_cannot_revoke",status(revoke(issued.get("id").asLong()))==409);
   JsonNode expired=issue();execute("UPDATE dnf_service.REGISTRATION_CODES SET expires_at=1 WHERE id="+expired.get("id").asLong());check("expired_rejected",status(call("client/register","POST",payload("reg_expired",expired.get("code").asText()),null))==400);
   JsonNode revoked=issue();check("revoke",status(revoke(revoked.get("id").asLong()))==200);check("revoked_rejected",status(call("client/register","POST",payload("reg_revoked",revoked.get("code").asText()),null))==400);
   JsonNode rollback=issue();execute("CREATE TRIGGER taiwan_cain_2nd.test_registration_failure BEFORE INSERT ON taiwan_cain_2nd.member_avatar_coin FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");
   MvcResult failedInsert=call("client/register","POST",payload("reg_rollback",rollback.get("code").asText()),null);
   check("late_insert_failure_rejected",status(failedInsert)>=400);
   String failureBody=failedInsert.getResponse().getContentAsString();
   check("internal_error_redacted",!failureBody.contains("SQL")&&!failureBody.contains("synthetic failure")&&!failureBody.contains("java.")&&!failureBody.contains("stackTrace"));
   check("failed_receipt_confirmed_not_applied",Integer.parseInt(query("SELECT COUNT(*) FROM dnf_service.DOF_REGISTRATION_RECEIPT WHERE state='NOT_APPLIED'"))>0);
   check("account_and_code_rollback","0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_rollback'"))&&"ready".equals(query("SELECT state FROM dnf_service.REGISTRATION_CODES WHERE id="+rollback.get("id").asLong())));
   check("no_orphan_native_rows","0".equals(query("SELECT COUNT(*) FROM d_taiwan.member_info m LEFT JOIN d_taiwan.accounts a ON a.UID=m.m_id WHERE a.UID IS NULL")));
   execute("DROP TRIGGER taiwan_cain_2nd.test_registration_failure");check("retry_after_rollback",status(call("client/register","POST",payload("reg_retried",rollback.get("code").asText()),null))==200);
   JsonNode auditFailure=issue();execute("CREATE TRIGGER dnf_service.test_registration_audit_failure BEFORE INSERT ON dnf_service.REGISTRATION_AUDIT FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure'");
   check("audit_failure_rejected",status(call("client/register","POST",payload("reg_audit_failed",auditFailure.get("code").asText()),null))==503);
   check("audit_failure_rolls_back_account_and_code","0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_audit_failed'"))&&"ready".equals(query("SELECT state FROM dnf_service.REGISTRATION_CODES WHERE id="+auditFailure.get("id").asLong())));
   execute("DROP TRIGGER dnf_service.test_registration_audit_failure");
   execute("ALTER TABLE taiwan_cain_2nd.member_avatar_coin ENGINE=MyISAM");check("nontransaction_engine_rejected",status(call("client/register","POST",payload("reg_bad_engine",code),null))==409);
   check("disable_allowed_on_unsupported_engine",status(policy(false))==200);
   check("gate_off_nontransaction_registration_rejected",status(call("client/register","POST",payload("reg_off_bad_engine",null),null))==409);
   execute("ALTER TABLE taiwan_cain_2nd.member_avatar_coin ENGINE=InnoDB");check("disable_preserves_codes","5".equals(query("SELECT COUNT(*) FROM dnf_service.REGISTRATION_CODES")));
   execute("ALTER TABLE dnf_service.DOF_REGISTRATION_RECEIPT ADD unknown_registration_field INT");
   check("unknown_management_schema_stops_registration",status(call("client/register","POST",payload("reg_unknown_management",null),null))==409&&"0".equals(query("SELECT COUNT(*) FROM d_taiwan.accounts WHERE accountname='reg_unknown_management'")));
   execute("ALTER TABLE dnf_service.DOF_REGISTRATION_RECEIPT DROP COLUMN unknown_registration_field");
   String receiptId=UUID.randomUUID().toString();
   execute("INSERT INTO dnf_service.DOF_REGISTRATION_RECEIPT(instance_id,request_id,name_tag,request_tag,key_id,state,ip_tag) SELECT instance_id,'"+receiptId+"',SHA2('synthetic pending name',256),request_tag,key_id,'PREPARED',ip_tag FROM dnf_service.DOF_REGISTRATION_RECEIPT WHERE state='PERSISTED' LIMIT 1");
   JsonNode receiptState=body(call(p+"/receipts/"+receiptId,"GET",null,token));Map<String,Object> receiptEdit=edit("check-receipt",receiptState.get("revision").asText());receiptEdit.put("targetRequestId",receiptId);
   check("pending_receipt_can_be_closed_without_native_writes",status(save(p+"/receipts/"+receiptId+"/reconcile","POST",receiptEdit))==200&&"NOT_APPLIED".equals(query("SELECT state FROM dnf_service.DOF_REGISTRATION_RECEIPT WHERE request_id='"+receiptId+"'")));
   JsonNode policyNow=body(call(p+"/policy","GET",null,token));Map<String,Object> conflict=edit("policy",policyNow.get("revision").asText());conflict.put("enabled",true);
   check("conflict_preview",status(call(p+"/preview","POST",conflict,token))==200);
   execute("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET state='PREPARED' WHERE request_id='"+conflict.get("requestId")+"'");execute("UPDATE dnf_service.REGISTRATION_POLICY SET revision=revision+7");
   check("foreign_change_is_conflict","CONFLICT".equals(body(call(p+"/operations/"+conflict.get("requestId")+"/reconcile","POST",null,token)).path("state").asText()));
   String foreignRevision=query("SELECT revision FROM dnf_service.REGISTRATION_POLICY");JsonNode current=body(call(p+"/operations/"+conflict.get("requestId")+"/current","GET",null,token));Map<String,Object> ack=edit("accept-current",current.get("revision").asText());ack.put("targetRequestId",conflict.get("requestId"));
   check("explicit_keep_current_closes_conflict",status(save(p+"/operations/"+conflict.get("requestId")+"/accept-current","POST",ack))==200&&"ACKNOWLEDGED".equals(query("SELECT state FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+conflict.get("requestId")+"'"))&&foreignRevision.equals(query("SELECT revision FROM dnf_service.REGISTRATION_POLICY")));
   String journal=query("SELECT GROUP_CONCAT(CONCAT(before_json,after_json)) FROM dnf_service.DOF_OPERATION_JOURNAL");String publicReceipts=call(p+"/receipts","GET",null,token).getResponse().getContentAsString();
   check("ordinary_journal_has_no_registration_secrets",!journal.contains(code)&&!journal.contains(RegistrationCodesService.hash(code))&&!journal.contains("synthetic-password-only")&&!journal.contains("203.0.113.20")&&!publicReceipts.contains("ip_tag")&&!publicReceipts.contains("request_tag"));
   String audit=call(p+"/history","GET",null,token).getResponse().getContentAsString();check("audit_has_no_secrets",!audit.contains(code)&&json.readTree(audit).size()>=2);
  }finally{System.out.println("REGISTRATION_CHECKS:"+json.writeValueAsString(checks));}
 }
}
