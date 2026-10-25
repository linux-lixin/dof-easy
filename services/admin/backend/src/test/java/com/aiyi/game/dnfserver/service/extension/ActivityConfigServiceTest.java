package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ActivityConfigServiceTest {
    private ActivityConfigService service;
    private AccountVODao accounts;
    private DataSource db;
    @Before public void setup() {
        com.aiyi.core.util.thread.ThreadUtil.setUserId(1L);
        service=new ActivityConfigService();accounts=mock(AccountVODao.class);db=mock(DataSource.class);
        ReflectionTestUtils.setField(service,"accountVODao",accounts);
        ReflectionTestUtils.setField(service,"dataSource",db);
        AccountVO admin=mock(AccountVO.class);when(admin.isAdmin()).thenReturn(true);
        when(accounts.get(anyLong())).thenReturn(admin);
    }
    private ActivityConfigService.Section section(String key) {return service.catalog().stream().filter(s->s.key.equals(key)).findFirst().get();}
    private Map<String,Object> values(ActivityConfigService.Section s) {
        Map<String,Object> v=new LinkedHashMap<>();for(ActivityConfigService.Field f:s.fields) v.put(f.name,f.initial);
        if(s.key.equals("signin")){v.put("d_Name","测试签到");v.put("start_time","2026-01-01");v.put("end_time","2026-12-31");}
        if(s.key.equals("battle")){v.put("name","测试战令");v.put("end_date","2026-12-31 23:59:59");}
        if(s.key.equals("collection")){v.put("Dname","测试收集箱");v.put("te_date_end","2026-12-31 23:59:59");}
        return v;
    }
    private void fails(int status,Runnable op) {
        try{op.run();fail("expected failure");}catch(ActivityConfigService.Failure e){assertEquals(status,e.status);}
    }
    @Test public void rejectsNonAdminBeforeTouchingDatabase() {
        when(accounts.get(anyLong())).thenReturn(null);
        fails(403,()->service.catalog());fails(403,()->service.list("signin"));
        fails(403,()->service.history("signin"));fails(403,()->service.save("signin",null,false));
        verifyZeroInteractions(db);
    }
    @Test public void unknownSectionCannotBecomeSqlIdentifier() {
        fails(404,()->service.list("signin; DROP TABLE qk.battle_pass"));verifyZeroInteractions(db);
    }
    @Test public void acceptsAllFourNativeConfigurationShapes() {
        for(ActivityConfigService.Section s:service.catalog()) if(Arrays.asList("signin","battle","rewards","collection","online").contains(s.key)) assertEquals(s.fields.size(),service.validate(s,values(s),null,true).size());
    }
    @Test public void readOnlyNativeModulesRejectWritesBeforeDatabase() {
        fails(409,()->service.save("inheritance",null,false));verifyZeroInteractions(db);
    }
    @Test public void requiresMatchingItemAndCountAndBoundedLevel() {
        ActivityConfigService.Section s=section("rewards");Map<String,Object> v=values(s);
        v.put("item1",3340);fails(400,()->service.validate(s,v,null,true));
        v.put("item_code1",10);assertEquals(3340L,service.validate(s,v,null,true).get("item1"));
        v.put("lv",51);fails(400,()->service.validate(s,v,null,true));
    }
    @Test public void acceptsNativeRestrictionEightButRejectsUnknownTypes() {
        ActivityConfigService.Section s=section("shop");Map<String,Object> v=values(s);
        v.put("item_id",3340);v.put("item_num",1);v.put("restricted_type",8);assertEquals(8L,service.validate(s,v,null,true).get("restricted_type"));
        v.put("restricted_type",9);fails(400,()->service.validate(s,v,null,true));
    }
    @Test public void rejectsUnknownFieldsAndCoerciveNumbers() {
        ActivityConfigService.Section s=section("rewards");Map<String,Object> v=values(s);
        v.put("uid",123);fails(400,()->service.validate(s,v,null,true));v.remove("uid");
        for(Object bad:Arrays.asList(-1,1.5,"1 OR 1=1",Collections.singletonMap("x",1),2147483648L)) {
            v.put("item1",bad);fails(400,()->service.validate(s,v,null,true));
        }
    }
    @Test public void validatesRealDatesAndOrder() {
        ActivityConfigService.Section s=section("signin");Map<String,Object> v=values(s);
        v.put("start_time","2026-02-30");fails(400,()->service.validate(s,v,null,true));
        v.put("start_time","2027-01-01");fails(400,()->service.validate(s,v,null,true));
    }
    @Test public void protectsUnverifiedNativeFlagsAndLegacyNulls() {
        ActivityConfigService.Section s=section("collection");Map<String,Object> v=values(s);
        v.put("Isunique",0);fails(400,()->service.validate(s,v,null,true));
        ActivityConfigService.Section sign=section("signin");Map<String,Object> old=values(sign);old.put("code_4",null);old.put("num_4",null);
        assertNull(service.validate(sign,new LinkedHashMap<>(old),old,false).get("code_4"));
    }
}
