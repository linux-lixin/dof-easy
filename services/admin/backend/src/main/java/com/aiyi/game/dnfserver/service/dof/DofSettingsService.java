package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Explicit multi-table config writes. No transaction rollback is assumed for MyISAM. */
@Service
public class DofSettingsService {
    @Resource private DofAdminService access;
    private final ObjectMapper json=new ObjectMapper();
    private static final String AUDIT="`dnf_service`.`DOF_SETTINGS_AUDIT`";
    private static final List<String> TABLES=Arrays.asList("billing","config");
    public static class Edit { public String revision,requestId; public Map<String,JsonNode> values; }
    private String instance(){return access.context().get("instanceId").toString();}
    private String encode(Map<String,String> state) {
        try{return json.writeValueAsString(state);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private String revision(Map<String,String> state){return NativeUiState.sha256(encode(state).getBytes(StandardCharsets.UTF_8));}
    private Map<String,String> snapshot(String text,String expected) {
        try {
            Map<String,String> data=json.readValue(text,new TypeReference<Map<String,String>>(){}),ordered=new LinkedHashMap<>();
            if(!data.keySet().equals(new HashSet<>(NativeGeneralSettings.COLUMNS)))throw new IllegalArgumentException();
            for(String key:NativeGeneralSettings.COLUMNS) {
                String value=data.get(key);
                if(value!=null && (value.length()>2*1024*1024 || !Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value)).equals(value)))throw new IllegalArgumentException();
                ordered.put(key,value);
            }
            if(!revision(ordered).equals(expected))throw new IllegalArgumentException();return ordered;
        } catch(Exception e){throw new DofFailure(409,"操作快照或校验损坏，停止覆盖");}
    }
    private void schema(Connection c)throws SQLException {
        Map<String,String> engines=new HashMap<>(),columns=new HashMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME IN ('billing','config')")) {
            while(r.next())engines.put(r.getString(1),r.getString(2));
        }
        for(String table:TABLES)if(!Arrays.asList("MyISAM","InnoDB").contains(engines.get(table)))
            throw new DofFailure(409,"原生表引擎缺失或不受支持，停止保存");
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME IN ('billing','config')")) {
            while(r.next()) {
                String key=r.getString(1)+"."+r.getString(2),type=r.getString(3);
                if("int".equals(type) && !r.getString(4).toLowerCase(Locale.ROOT).contains("unsigned"))columns.put(key,"signed-int");
                if("mediumtext".equals(type) && Arrays.asList("utf8","utf8mb3","utf8mb4").contains(r.getString(5)))columns.put(key,"native-text");
            }
        }
        for(String table:TABLES)if(!"signed-int".equals(columns.get(table+".ID")))throw new DofFailure(409,"原生编号字段不兼容，不初始化配置");
        for(String key:NativeGeneralSettings.COLUMNS)if(!(key.endsWith(".Switch")?"native-text":"signed-int").equals(columns.get(key)))
            throw new DofFailure(409,"原生设置字段类型或字符集不兼容，停止覆盖");
    }
    private Map<String,String> read(Connection c)throws SQLException {
        schema(c);
        Map<String,String> found=new HashMap<>(),ordered=new LinkedHashMap<>();
        for(String table:TABLES) {
            List<String> keys=new ArrayList<>();for(String key:NativeGeneralSettings.COLUMNS)if(key.startsWith(table+"."))keys.add(key);
            List<String> columns=new ArrayList<>();for(String key:keys)columns.add("BINARY `"+key.substring(table.length()+1)+"`");
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,"+String.join(",",columns)+" FROM `dof_native`.`"+table+"` LIMIT 2")) {
                if(!r.next() || r.getInt(1)!=1)throw new DofFailure(409,"原生设置缺失，不自动初始化");
                for(int i=0;i<keys.size();i++) {
                    byte[] value=r.getBytes(i+2);if(value!=null && value.length>1024*1024)throw new DofFailure(409,"原生设置超过读取上限");
                    found.put(keys.get(i),value==null?null:Base64.getEncoder().encodeToString(value));
                }
                if(r.next())throw new DofFailure(409,"原生设置存在多行，停止覆盖");
            }
        }
        for(String key:NativeGeneralSettings.COLUMNS)ordered.put(key,found.get(key));return ordered;
    }
    private void lock(Connection c)throws SQLException {
        // Table write locks serialize native MyISAM data with durable InnoDB audit.
        // The bound connection uses autocommit; never promise transactional undo.
        try(Statement s=c.createStatement()){s.execute("LOCK TABLES `dof_native`.`billing` WRITE,`dof_native`.`config` WRITE,"+AUDIT+" WRITE");}
        // Check while locks prevent concurrent DDL from replacing the audit table.
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_SETTINGS_AUDIT'")) {
            if(!r.next() || !"InnoDB".equals(r.getString(1)) || r.next())throw new DofFailure(409,"审计存储引擎不兼容，停止保存");
        }
    }
    private void unlock(Connection c)throws SQLException {try(Statement s=c.createStatement()){s.execute("UNLOCK TABLES");}}
    private void request(Edit edit) {
        if(edit==null || edit.revision==null || !edit.revision.matches("[0-9a-f]{64}") || !validId(edit.requestId))throw new DofFailure(400,"缺少配置版本或操作编号");
    }
    private boolean validId(String id){return id!=null && id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");}
    private void sameRevision(Map<String,String> state,String expected) {
        if(!revision(state).equals(expected))throw new DofFailure(409,"设置已被其他操作修改，请重新读取并预览");
    }
    private List<String> changed(Map<String,String> a,Map<String,String> b) {
        List<String> out=new ArrayList<>();for(String key:NativeGeneralSettings.COLUMNS)if(!Objects.equals(a.get(key),b.get(key)))out.add(key);return out;
    }
    private Map<String,Object> envelope(String module,Map<String,String> state) {
        Map<String,Object> out=access.context();out.put("module",module);out.put("revision",revision(state));out.put("fields",NativeGeneralSettings.inspect(module,state));
        out.put("effectState","not_observed");out.put("activation","已保存仅表示原生配置持久化；QK 读取与客户端实际效果仍需核对。网页不执行奖励发放。");return out;
    }
    public Object read(String module) {
        access.admin();NativeGeneralSettings.module(module);
        try(Connection c=access.connection()){return envelope(module,read(c));}catch(SQLException e){throw new DofFailure(503,"原生设置或隔离数据库不可用");}
    }
    public Object preview(String module,Edit edit) {
        access.admin();NativeGeneralSettings.module(module);request(edit);
        try(Connection c=access.connection()) {
            Map<String,String> before=read(c);sameRevision(before,edit.revision);Map<String,String> after=NativeGeneralSettings.apply(module,before,edit.values);
            Map<String,Object> out=envelope(module,after);List<Map<String,Object>> changes=new ArrayList<>();
            for(NativeGeneralSettings.Field f:NativeGeneralSettings.FIELDS.values())if(f.module.equals(module)) {
                Object a=NativeGeneralSettings.value(before,f),b=NativeGeneralSettings.value(after,f);
                if(!Objects.equals(a,b)){Map<String,Object> row=new LinkedHashMap<>();row.put("label",f.label);row.put("before",a);row.put("after",b);changes.add(row);}
            }
            out.put("changes",changes);out.put("persisted",false);return out;
        }catch(SQLException e){throw new DofFailure(503,"数据库不可用，尚未保存");}
    }
    private Map<String,Object> audit(Connection c,String id,boolean privateFields)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT actor,preset,operation_name,state,before_revision,after_revision,target_request,before_json,after_json,created_at,adapter_version FROM "+AUDIT+" WHERE instance_id=? AND request_id=?")) {
            p.setString(1,instance());p.setString(2,id);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next())return null;Map<String,Object> row=access.context();row.put("requestId",id);row.put("actor",r.getLong(1));row.put("module",r.getString(2));row.put("operation",r.getString(3));row.put("state",r.getString(4));row.put("beforeRevision",r.getString(5));row.put("afterRevision",r.getString(6));row.put("targetRequest",r.getString(7));row.put("createdAt",r.getString(10));row.put("effectState","not_observed");
                row.put("persisted",Arrays.asList("PERSISTED","NO_CHANGE").contains(r.getString(4)));
                if(privateFields){row.put("beforeJson",r.getString(8));row.put("afterJson",r.getString(9));row.put("adapter",r.getString(11));}return row;
            }
        }
    }
    private void finish(Connection c,String id,String state)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("UPDATE "+AUDIT+" SET state=?,finished_at=CURRENT_TIMESTAMP WHERE instance_id=? AND request_id=?")) {
            p.setString(1,state);p.setString(2,instance());p.setString(3,id);if(p.executeUpdate()!=1)throw new SQLException("Audit completion row missing");
        }
    }
    private Map<String,String> before(Map<String,Object> row){return snapshot(row.get("beforeJson").toString(),row.get("beforeRevision").toString());}
    private Map<String,String> after(Map<String,Object> row){return snapshot(row.get("afterJson").toString(),row.get("afterRevision").toString());}
    private void compatible(Map<String,Object> row,String module) {
        if(row==null || !module.equals(row.get("module")) || !DofAdminService.VERSION.equals(row.get("adapter")))throw new DofFailure(409,"操作记录不存在或适配版本不一致");
    }
    private Map<String,String> restoration(Map<String,Object> row,Map<String,String> current) {
        if(!Arrays.asList("PERSISTED","PREPARED","PARTIAL").contains(row.get("state")))throw new DofFailure(409,"此记录没有可恢复的修改");
        Map<String,String> old=before(row),target=after(row),restored=new LinkedHashMap<>(current);
        for(String key:changed(old,target)) {
            if(!Objects.equals(current.get(key),target.get(key)) && !Objects.equals(current.get(key),old.get(key)))throw new DofFailure(409,"本次涉及的字段已有其他修改，停止恢复");
            restored.put(key,old.get(key));
        }
        return restored;
    }
    private void write(Connection c,Map<String,String> before,Map<String,String> after)throws SQLException {
        List<String> changes=changed(before,after);
        for(String table:TABLES) {
            List<String> keys=new ArrayList<>(),assignments=new ArrayList<>();
            for(String key:changes)if(key.startsWith(table+".")){keys.add(key);assignments.add("`"+key.substring(table.length()+1)+"`=?");}
            if(keys.isEmpty())continue;
            try(PreparedStatement p=c.prepareStatement("UPDATE `dof_native`.`"+table+"` SET "+String.join(",",assignments)+" WHERE ID=1")) {
                for(int i=0;i<keys.size();i++) {
                    String key=keys.get(i),value=after.get(key);
                    if(key.equals("config.Switch"))p.setBytes(i+1,value==null?null:Base64.getDecoder().decode(value));
                    else if(value==null)p.setNull(i+1,Types.INTEGER);
                    else {
                        // Connector/J may emit setBytes as a hex literal. In an
                        // integer column X'3530' becomes 13616, not decimal 50.
                        String number=NativeGeneralSettings.raw(after,key);
                        if(!number.matches("-?[0-9]{1,10}"))throw new DofFailure(409,"数值快照格式损坏，停止恢复");
                        try{p.setInt(i+1,Integer.parseInt(number));}catch(NumberFormatException e){throw new DofFailure(409,"数值快照超出原生整数范围");}
                    }
                }
                if(p.executeUpdate()!=1)throw new SQLException("Native row count changed");
            }
        }
        if(!revision(read(c)).equals(revision(after)))throw new SQLException("Native persistence readback mismatch");
    }
    public Object save(String module,Edit edit,String restoreId) {
        long actor=access.admin();NativeGeneralSettings.module(module);request(edit);
        if(restoreId!=null && (!validId(restoreId) || edit.values!=null || restoreId.equals(edit.requestId)))throw new DofFailure(400,"恢复必须使用独立请求编号且不能混入新设置");
        boolean prepared=false;
        try(Connection c=access.connection()) {
            lock(c);try {
                String operation=restoreId==null?"edit":"restore";Map<String,Object> previous=audit(c,edit.requestId,true);
                if(previous!=null) {
                    compatible(previous,module);
                    if(((Number)previous.get("actor")).longValue()!=actor || !operation.equals(previous.get("operation")) || !Objects.equals(restoreId,previous.get("targetRequest")) || !edit.revision.equals(previous.get("beforeRevision")))throw new DofFailure(409,"操作编号已用于其他请求");
                    if(restoreId==null && !revision(NativeGeneralSettings.apply(module,before(previous),edit.values)).equals(previous.get("afterRevision")))throw new DofFailure(409,"同一编号的提交内容不同");
                    return audit(c,edit.requestId,false);
                }
                try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? AND state IN ('PREPARED','PARTIAL')")) {
                    p.setString(1,instance());try(ResultSet r=p.executeQuery()){while(r.next())if(!r.getString(1).equals(restoreId))throw new DofFailure(409,"存在结果待核对的操作："+r.getString(1));}
                }
                Map<String,String> current=read(c);sameRevision(current,edit.revision);Map<String,String> target;
                if(restoreId==null)target=NativeGeneralSettings.apply(module,current,edit.values);
                else {Map<String,Object> row=audit(c,restoreId,true);compatible(row,module);target=restoration(row,current);}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO "+AUDIT+" (instance_id,request_id,actor,adapter_version,preset,operation_name,target_request,before_revision,after_revision,before_json,after_json,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,'PREPARED')")) {
                    p.setString(1,instance());p.setString(2,edit.requestId);p.setLong(3,actor);p.setString(4,DofAdminService.VERSION);p.setString(5,module);p.setString(6,operation);p.setString(7,restoreId);p.setString(8,revision(current));p.setString(9,revision(target));p.setString(10,encode(current));p.setString(11,encode(target));p.executeUpdate();prepared=true;
                }
                write(c,current,target);
                // Mark the target recovered first; an interrupted audit completion
                // still leaves the recovery request PREPARED, blocking new writes.
                if(restoreId!=null)finish(c,restoreId,"RESTORED");
                finish(c,edit.requestId,changed(current,target).isEmpty()?"NO_CHANGE":"PERSISTED");return audit(c,edit.requestId,false);
            }finally{unlock(c);}
        }catch(SQLException e){throw new DofFailure(503,prepared?"保存结果待核对；不要重新提交，请保留操作编号："+edit.requestId:"数据库或审计不可用，未确认保存");}
    }
    public Object reconcile(String module,String id) {
        access.admin();NativeGeneralSettings.module(module);if(!validId(id))throw new DofFailure(400,"操作编号无效");
        try(Connection c=access.connection()) {
            lock(c);try {
                Map<String,Object> row=audit(c,id,true);compatible(row,module);
                if(Arrays.asList("PREPARED","PARTIAL").contains(row.get("state"))) {
                    Map<String,String> old=before(row),target=after(row),current=read(c);List<String> keys=changed(old,target);boolean allBefore=true,allAfter=true;
                    for(String key:keys) {
                        boolean b=Objects.equals(current.get(key),old.get(key)),a=Objects.equals(current.get(key),target.get(key));
                        if(!b && !a)throw new DofFailure(409,"存在第三方修改，保留现场，禁止自动重试或恢复");allBefore&=b;allAfter&=a;
                    }
                    if(allAfter) {
                        Object restored=row.get("targetRequest");if(restored!=null)finish(c,restored.toString(),"RESTORED");
                        finish(c,id,keys.isEmpty()?"NO_CHANGE":"PERSISTED");
                    } else finish(c,id,allBefore?"NOT_APPLIED":"PARTIAL");
                }
                return audit(c,id,false);
            }finally{unlock(c);}
        }catch(SQLException e){throw new DofFailure(503,"核对失败，保留操作编号和现场");}
    }
    public Object history(String module) {
        access.admin();NativeGeneralSettings.module(module);
        try(Connection c=access.connection()) {
            List<String> ids=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? AND preset=? ORDER BY id DESC LIMIT 100")) {
                p.setString(1,instance());p.setString(2,module);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}
            }
            List<Object> rows=new ArrayList<>();for(String id:ids)rows.add(audit(c,id,false));return rows;
        }catch(SQLException e){throw new DofFailure(503,"无法读取设置操作记录");}
    }
}
