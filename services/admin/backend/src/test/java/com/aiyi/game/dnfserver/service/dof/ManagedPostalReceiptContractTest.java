package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual SQL in the existing disposable fixture; no native player or original-tool claim. */
public class ManagedPostalReceiptContractTest {
    private ManagedPostalContractTest fixture;private ManagedPostalService postal;private ManagedPostalReceiptService controls;
    private DofAdminService access;private DriverManagerDataSource ds;
    @Before public void setup()throws Exception{
        fixture=new ManagedPostalContractTest();fixture.setup();postal=(ManagedPostalService)ReflectionTestUtils.getField(fixture,"service");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");
        controls=new ManagedPostalReceiptService();ReflectionTestUtils.setField(controls,"postal",postal);ReflectionTestUtils.setField(controls,"journal",ReflectionTestUtils.getField(postal,"journal"));
    }
    @After public void cleanup()throws Exception{if(fixture!=null){if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);fixture.cleanup();}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private void reject(int status,Runnable action){try{action.run();fail("Unsafe receipt control accepted");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private ManagedPostalService.Edit interrupted(boolean myisam)throws Exception{
        if(myisam)sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");
        ManagedPostalService.Edit e=ReflectionTestUtils.invokeMethod(fixture,"edit","{\"gold\":1,\"message\":\"synthetic receipt control\"}");postal.preview(123,e);
        ReflectionTestUtils.invokeMethod(fixture,"fault","UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->postal.save(e));ReflectionTestUtils.setField(access,"dataSource",ds);return e;
    }
    private ManagedPostalReceiptService.Edit edit(String source){
        Map<?,?> row=((List<?>)postal.history(123)).stream().map(this::map).filter(r->source.equals(r.get("requestId"))).findFirst().get();
        ManagedPostalReceiptService.Edit e=new ManagedPostalReceiptService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=(String)row.get("receiptRevision");e.reason="synthetic manual review; original outcome stays unknown";return e;
    }
    private void acknowledgmentFault(boolean afterStatement)throws Exception{
        AtomicBoolean once=new AtomicBoolean(true);DataSource broken=mock(DataSource.class);
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            try{Object out=m.invoke(c,args);
                if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=CASE")){
                    PreparedStatement real=(PreparedStatement)out;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,method,values)->{
                        try{if(method.getName().equals("executeUpdate")&&once.compareAndSet(true,false)){if(afterStatement)method.invoke(real,values);throw new SQLException("synthetic acknowledgment disconnect");}return method.invoke(real,values);}catch(InvocationTargetException e){throw e.getCause();}
                    });
                }return out;
            }catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);
    }
    @Test public void deletedRoleDoesNotBlockInnoDbReconciliation()throws Exception{
        ManagedPostalService.Edit e=interrupted(false);sql("DELETE FROM taiwan_cain.charac_info");sql("DELETE FROM d_taiwan.accounts WHERE UID=22");reject(404,()->postal.status(123));
        assertEquals("NOT_APPLIED",map(postal.reconcile(123,e.requestId)).get("state"));assertEquals("NOT_APPLIED",map(postal.save(e)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));
    }
    @Test public void myisamDeletedRoleCanBeAcknowledgedWithoutResendOrDeletingNativeRows()throws Exception{
        ManagedPostalService.Edit e=interrupted(true);sql("DELETE FROM taiwan_cain.charac_info");assertEquals("CONFLICT",map(postal.reconcile(123,e.requestId)).get("state"));
        ManagedPostalReceiptService.Edit ack=edit(e.requestId);assertEquals("PREVIEWED",map(controls.preview(123,e.requestId,ack)).get("state"));assertEquals("NO_CHANGE",map(controls.save(123,e.requestId,ack)).get("state"));
        assertEquals(false,map(controls.save(123,e.requestId,ack)).get("nativeDataChanged"));assertEquals("ACKNOWLEDGED",map(postal.reconcile(123,e.requestId)).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));assertEquals(1,((List<?>)controls.history(123)).size());
    }
    @Test public void lostAcknowledgmentReplyRecoversSameAtomicReceipt()throws Exception{
        ManagedPostalService.Edit e=interrupted(true);postal.reconcile(123,e.requestId);ManagedPostalReceiptService.Edit ack=edit(e.requestId);controls.preview(123,e.requestId,ack);acknowledgmentFault(true);reject(503,()->controls.save(123,e.requestId,ack));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("NO_CHANGE",map(controls.reconcile(123,e.requestId,ack.requestId)).get("state"));assertEquals("NO_CHANGE",map(controls.save(123,e.requestId,ack)).get("state"));assertEquals("ACKNOWLEDGED",map(postal.reconcile(123,e.requestId)).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void failureBeforeAcknowledgmentProvesOnlyMetadataNotApplied()throws Exception{
        ManagedPostalService.Edit e=interrupted(true);postal.reconcile(123,e.requestId);ManagedPostalReceiptService.Edit ack=edit(e.requestId);controls.preview(123,e.requestId,ack);acknowledgmentFault(false);reject(503,()->controls.save(123,e.requestId,ack));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("NOT_APPLIED",map(controls.reconcile(123,e.requestId,ack.requestId)).get("state"));assertEquals("NOT_APPLIED",map(controls.save(123,e.requestId,ack)).get("state"));assertEquals("CONFLICT",map(postal.reconcile(123,e.requestId)).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void competingAcknowledgmentsAndReusedIdentifiersCannotRewriteSource()throws Exception{
        ManagedPostalService.Edit e=interrupted(true);postal.reconcile(123,e.requestId);ManagedPostalReceiptService.Edit a=edit(e.requestId),b=edit(e.requestId);controls.preview(123,e.requestId,a);controls.preview(123,e.requestId,b);controls.save(123,e.requestId,a);reject(409,()->controls.save(123,e.requestId,b));
        a.reason="changed content";reject(409,()->controls.save(123,e.requestId,a));reject(409,()->controls.preview(123,e.requestId,edit(e.requestId)));reject(404,()->controls.reconcile(124,e.requestId,b.requestId));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void newAdminAcknowledgesWithoutRewritingOriginalActor()throws Exception{
        ManagedPostalService.Edit e=interrupted(true);postal.reconcile(123,e.requestId);ManagedPostalReceiptService.Edit ack=edit(e.requestId);
        sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");sql("INSERT INTO d_taiwan.accounts VALUES(2,'second_admin',7)");AccountVODao accounts=mock(AccountVODao.class);AccountVO admin=new AccountVO();admin.setUid(2L);admin.setAdmin(true);when(accounts.get(2L)).thenReturn(admin);ReflectionTestUtils.setField(access,"accountVODao",accounts);ThreadUtil.setUserId(2L);
        controls.preview(123,e.requestId,ack);assertEquals(2L,map(controls.save(123,e.requestId,ack)).get("actor"));assertEquals("1:2",scalar("SELECT CONCAT(actor,':',checked_by) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=2");reject(403,()->controls.history(123));
    }
    @Test public void schemaChangeStillBlocksDeletedRoleResultInference()throws Exception{
        ManagedPostalService.Edit e=interrupted(false);sql("DELETE FROM taiwan_cain.charac_info");sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");reject(409,()->postal.reconcile(123,e.requestId));assertEquals("PREPARED",scalar("SELECT state FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));
    }
}
