package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Edits only native activity configuration. Player progress is never a write target. */
@Service
public class ActivityConfigService {
    @Resource private DataSource dataSource;
    @Resource private AccountVODao accountVODao;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Section> sections;
    private static final String AUDIT = "`dnf_service`.`EXTENSION_CONFIG_AUDIT`";

    public static class Field {
        public String name, label, type, group;
        public long min, max;
        public boolean readonly;
        @com.fasterxml.jackson.annotation.JsonProperty("default") public Object initial;
    }
    public static class Section {
        public String key, title, table, primaryKey;
        public boolean autoId;
        public boolean writable = true, deleteAllowed = true;
        public String notice;
        public List<Field> fields;
    }
    public static class Edit {
        public String revision;
        public Map<String,Object> values;
    }
    public static class Failure extends RuntimeException {
        public final int status;
        public Failure(int status, String message) { super(message); this.status = status; }
    }
    public ActivityConfigService() {
        try (InputStream in = getClass().getResourceAsStream("/extension/activities.json")) {
            sections = json.readValue(in, new TypeReference<List<Section>>() {});
        } catch (Exception e) { throw new IllegalStateException("活动字段清单无法加载", e); }
    }
    private long admin() {
        AccountVO user = accountVODao.get(ThreadUtil.getUserId());
        if (user == null || !user.isAdmin()) throw new Failure(403, "仅管理员可以管理活动配置");
        return ThreadUtil.getUserId();
    }
    private Section section(String key) {
        return sections.stream().filter(s -> s.key.equals(key)).findFirst()
            .orElseThrow(() -> new Failure(404, "未知活动配置"));
    }
    private String table(Section s) { return "`qk`.`" + s.table + "`"; }
    private String columns(Section s) {
        return s.fields.stream().map(f -> "`" + f.name + "`").collect(Collectors.joining(","));
    }
    public List<Section> catalog() { admin(); return sections; }

    private void schema(Connection c, Section s) throws SQLException {
        Map<String,String> actual = new HashMap<>();
        try (PreparedStatement p = c.prepareStatement("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='qk' AND TABLE_NAME=?")) {
            p.setString(1, s.table);
            try (ResultSet r = p.executeQuery()) { while(r.next()) actual.put(r.getString(1), r.getString(2)); }
        }
        if (actual.isEmpty()) throw new Failure(409, "扩展配置表尚未初始化，请先启用团本扩展并完成游戏服务启动");
        for (Field f : s.fields) if (!f.type.equals(actual.get(f.name)))
            throw new Failure(409, "配置表版本不兼容，已停止读写，请核对扩展版本");
    }
    private List<Map<String,Object>> rows(Connection c, Section s, Long id) throws SQLException {
        String where = id == null ? "" : " WHERE `" + s.primaryKey + "`=?";
        String order = s.primaryKey == null ? "" : " ORDER BY `" + s.primaryKey + "`";
        try (PreparedStatement p = c.prepareStatement("SELECT " + columns(s) + " FROM " + table(s) + where + order + " LIMIT 1001")) {
            if(id != null) p.setLong(1,id);
            try(ResultSet r=p.executeQuery()) {
                List<Map<String,Object>> result=new ArrayList<>();
                while(r.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(Field f:s.fields) {
                        Object value = "int".equals(f.type) ? r.getObject(f.name) : r.getString(f.name);
                        // Connector/J 5.1 may append .0 to DATETIME strings; native schema has no fractional seconds.
                        if (value != null && "datetime".equals(f.type)) value = value.toString().replaceFirst("\\.0+$", "");
                        row.put(f.name, value);
                    }
                    result.add(row);
                }
                if(s.primaryKey==null && result.size()>1) throw new Failure(409,"活动设置存在多条记录，拒绝覆盖，请先核查原始配置");
                if(result.size()>1000) throw new Failure(409,"配置超过本页支持的 1000 条，请先整理配置后再编辑");
                return result;
            }
        }
    }
    private String revision(Map<String,Object> row) {
        if(row==null) return "new";
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(row));
            StringBuilder out=new StringBuilder();
            for(byte b:bytes) out.append(String.format("%02x",b & 255));
            return out.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private Map<String,Object> envelope(Map<String,Object> row) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("values",row);out.put("revision",revision(row));return out;
    }
    public Map<String,Object> list(String key) {
        admin();Section s=section(key);
        try(Connection c=dataSource.getConnection()) {
            schema(c,s);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("writable",s.writable);
            out.put("rows",rows(c,s,null).stream().map(this::envelope).collect(Collectors.toList()));
            out.put("moduleNotice",s.notice);
            out.put("notice","配置保存到游戏插件使用的数据库。领取与进度继续由游戏处理；请重新打开游戏活动窗口验证，必要时安排整实例重启。");
            return out;
        } catch(SQLException e) { throw unavailable(); }
    }
    private Failure unavailable() { return new Failure(503,"活动数据库暂不可用或权限不足；未自动初始化或覆盖原生表"); }
    // Strict scalar validation: never accept SQL names, nested objects, coercive numbers or unknown fields.
    Map<String,Object> validate(Section s, Map<String,Object> input, Map<String,Object> old, boolean creating) {
        if(input==null) throw new Failure(400,"配置不能为空");
        Set<String> names=s.fields.stream().map(f->f.name).collect(Collectors.toSet());
        if(!names.equals(input.keySet())) throw new Failure(400,"配置字段缺失或包含未知字段，请刷新页面");
        Map<String,Object> result=new LinkedHashMap<>();
        for(Field f:s.fields) {
            Object value=input.get(f.name);
            if(f.readonly) {
                Object expected=old==null ? f.initial : old.get(f.name);
                if(!Objects.equals(String.valueOf(value),String.valueOf(expected))) throw new Failure(400,f.label+"不允许在此修改");
                result.put(f.name,expected);continue;
            }
            if(s.autoId && creating && f.name.equals(s.primaryKey)) { result.put(f.name,0L);continue; }
            if(value==null) { // Preserve legacy nullable reward cells; new cells use explicit zero.
                if(old!=null && old.get(f.name)==null) {result.put(f.name,null);continue;}
                throw new Failure(400,f.label+"不能为空");
            }
            String text=String.valueOf(value);
            if("int".equals(f.type)) {
                if(!text.matches("[0-9]{1,10}")) throw new Failure(400,f.label+"必须为整数");
                long n=Long.parseLong(text);
                if(n<f.min || n>f.max) throw new Failure(400,f.label+"超出允许范围");
                result.put(f.name,n);
            } else if("varchar".equals(f.type)) {
                if(!(value instanceof String) || text.length()>255 || text.codePoints().anyMatch(n->n<32 || n>65535)) throw new Failure(400,f.label+"格式错误或超过 255 字符");
                if((f.name.equals("name") || f.name.equals("d_Name") || f.name.equals("Dname")) && text.trim().isEmpty()) throw new Failure(400,"请填写活动名称");
                result.put(f.name,text);
            } else {
                try {
                    if(!text.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}"+("datetime".equals(f.type)?" [0-9]{2}:[0-9]{2}:[0-9]{2}":""))) throw new IllegalArgumentException();
                    if("date".equals(f.type)) LocalDate.parse(text); else LocalDateTime.parse(text,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));
                } catch(Exception e) {throw new Failure(400,f.label+"不是有效日期");}
                result.put(f.name,text);
            }
        }
        if(s.key.equals("signin")) {
            if(result.get("start_time").toString().compareTo(result.get("end_time").toString())>0) throw new Failure(400,"结束日期不能早于开始日期");
            for(int i=1;i<=28;i++) pair(result,"code_"+i,"num_"+i);
            for(int i=1;i<=4;i++) pair(result,"Cum_"+i,"C_num_"+i);
        }
        if(s.key.equals("rewards")) for(int i=1;i<=3;i++) pair(result,"item"+i,"item_code"+i);
        if(s.key.equals("charts-rewards")) {
            String[] ids=result.get("code_data").toString().split(",",-1), amounts=result.get("code_num").toString().split(",",-1);
            if(ids.length!=amounts.length || ids.length>50)throw new Failure(400,"排行奖励道具和数量不匹配");
            for(int i=0;i<ids.length;i++)if(!ids[i].matches("[1-9][0-9]{0,9}") || !amounts[i].matches("[1-9][0-9]{0,9}") || Long.parseLong(ids[i])>2147483647L || Long.parseLong(amounts[i])>2147483647L)throw new Failure(400,"排行奖励编号或数量无效");
        }
        if(s.key.equals("shop")){pair(result,"item_id","item_num");pair(result,"deduction_item_id","deduction_item_num");pair(result,"deduction_item_id2","deduction_item_num2");}
        if(s.key.equals("online")) {
            for(int i=1;i<=4;i++) {
                pair(result,"stage"+i+"_item_id","stage"+i+"_item_count");
                pair(result,"cumulative"+i+"_item_id","cumulative"+i+"_item_count");
                if(Long.parseLong(result.get("stage"+i+"_minutes").toString())<1 || Long.parseLong(result.get("cumulative"+i+"_required").toString())<1)
                    throw new Failure(400,"在线奖励时间和累计门槛必须大于零");
            }
        }
        return result;
    }
    private void pair(Map<String,Object> row,String a,String b) {
        long x=row.get(a)==null?0:Long.parseLong(row.get(a).toString()), y=row.get(b)==null?0:Long.parseLong(row.get(b).toString());
        if((x==0)!=(y==0)) throw new Failure(400,"奖励道具与数量必须同时填写；不发放时两项均为 0");
    }
    private void auditTable(Connection c) throws SQLException {
        try(Statement st=c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS "+AUDIT+" (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, actor BIGINT NOT NULL, section_name VARCHAR(32) NOT NULL, operation_name VARCHAR(16) NOT NULL, before_json MEDIUMTEXT NOT NULL, after_json MEDIUMTEXT NOT NULL, state VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8");
        }
    }
    private boolean referenced(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM `qk`.`Collection_box_user_item` WHERE Slot=? LIMIT 1")) {
            p.setLong(1,id);try(ResultSet r=p.executeQuery()) {return r.next();}
        }
    }
    private void guard(Connection c,Section s) throws SQLException {
        String sql=null;
        if(s.key.startsWith("pot-") && !s.key.equals("pot-switch"))sql="SELECT COUNT(*)=1 AND COALESCE(MAX(item_off_1),0)=0 AND COALESCE(MAX(item_off_2),0)=0 AND COALESCE(MAX(item_off_3),0)=0 FROM qk.earthenware_pot_item";
        if(s.key.equals("shop"))sql="SELECT (SELECT COUNT(*) FROM qk.new_shop)=1 AND (SELECT COUNT(*) FROM qk.new_shop WHERE swoff<>0)=0 AND (SELECT COUNT(*) FROM qk.timed_task WHERE task_state=1)=0";
        if(s.key.equals("charts-rewards"))sql="SELECT COUNT(*)=1 AND COALESCE(MAX(status),0)=0 FROM qk.game_charts_config";
        if(s.key.equals("appearance"))sql="SELECT COUNT(*)<=1 AND COALESCE(MAX(swoff),0)=0 FROM qk.transformation_item";
        if(sql!=null)try(Statement st=c.createStatement();ResultSet r=st.executeQuery(sql)){if(!r.next() || !r.getBoolean(1))throw new Failure(409,"原生功能仍在启用或开关状态不明确，拒绝改写，请先完成停用及兼容验证");}
    }
    public Map<String,Object> preview(String key, Edit edit) {
        admin();Section s=section(key);
        if(!s.writable) throw new Failure(409,"此配置只读，尚未完成规则验证");
        if(edit==null || edit.values==null || edit.revision==null)throw new Failure(400,"缺少配置与版本");
        boolean creating="new".equals(edit.revision);Long id=null;
        if(s.primaryKey!=null && !(creating && s.autoId)) {
            Object value=edit.values.get(s.primaryKey);
            if(value==null || !value.toString().matches(s.key.equals("charts-rewards")?"(?:0|[1-9][0-9]{0,9})":"[1-9][0-9]{0,9}"))throw new Failure(400,"记录编号无效");
            id=Long.parseLong(value.toString());if(id>2147483647L)throw new Failure(400,"记录编号无效");
        }
        try(Connection c=dataSource.getConnection()) {
            schema(c,s);guard(c,s);List<Map<String,Object>> found=creating && s.autoId?Collections.emptyList():rows(c,s,id);
            Map<String,Object> old=found.isEmpty()?null:found.get(0);
            if(!revision(old).equals(edit.revision))throw new Failure(409,"配置版本已改变，请重新预览");
            return envelope(validate(s,edit.values,old,creating));
        }catch(SQLException e){throw unavailable();}
    }
    public Map<String,Object> save(String key, Edit edit, boolean remove) {
        long actor=admin();Section s=section(key);
        if (!s.writable) throw new Failure(409, s.notice == null ? "此配置尚未完成规则验证，只读" : s.notice);
        if (remove && !s.deleteAllowed) throw new Failure(409,"此配置禁止删除，请停用对应功能");
        if(edit==null || edit.revision==null || edit.values==null) throw new Failure(400,"缺少配置版本");
        boolean creating="new".equals(edit.revision);
        if(remove && (creating || s.primaryKey==null)) throw new Failure(400,"不能删除活动设置");
        Long id=null;
        if(s.primaryKey!=null && !(creating && s.autoId)) {
            Object value=edit.values.get(s.primaryKey);
            if(value==null || !value.toString().matches(s.key.equals("charts-rewards")?"(?:0|[1-9][0-9]{0,9})":"[1-9][0-9]{0,9}")) throw new Failure(400,"记录编号无效");
            id=Long.parseLong(value.toString());
            if(id>2147483647L) throw new Failure(400,"记录编号超出范围");
        }
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(true);schema(c,s);auditTable(c);
            String locks=table(s)+" WRITE,"+AUDIT+" WRITE";
            if(s.key.startsWith("pot-") && !s.key.equals("pot-switch")) locks+=",qk.earthenware_pot_item READ";
            if(s.key.equals("shop"))locks+=",qk.new_shop READ,qk.timed_task READ";
            if(s.key.equals("charts-rewards"))locks+=",qk.game_charts_config READ";
            if(s.key.equals("collection")) locks+=",`qk`.`Collection_box_user_item` READ";
            // Native tables include MyISAM. Use explicit table locks, not fictional transaction rollback.
            try(Statement st=c.createStatement()) {st.execute("LOCK TABLES "+locks);}
            try {
                guard(c,s);
                List<Map<String,Object>> found=creating && s.autoId?Collections.emptyList():rows(c,s,id);
                Map<String,Object> old=found.isEmpty()?null:found.get(0);
                if(!revision(old).equals(edit.revision)) throw new Failure(409,"配置已被其他操作修改，请刷新后重新编辑");
                Map<String,Object> next=remove?null:validate(s,edit.values,old,creating);
                if (creating && s.key.equals("collection")) {
                    try (PreparedStatement p=c.prepareStatement("SELECT 1 FROM "+table(s)+" WHERE box_id=? AND item_id=? LIMIT 1")) {
                        p.setObject(1,next.get("box_id"));p.setObject(2,next.get("item_id"));
                        try(ResultSet r=p.executeQuery()){if(r.next()) throw new Failure(409,"同一收集箱已存在此道具槽位，请刷新，重复导入不会新增槽位");}
                    }
                }

                if(s.key.equals("collection") && old!=null && referenced(c,id)) {
                    if(remove || !String.valueOf(old.get("box_id")).equals(String.valueOf(next.get("box_id"))) || !String.valueOf(old.get("item_id")).equals(String.valueOf(next.get("item_id"))))
                        throw new Failure(409,"该收集槽已有玩家记录，不能删除或更换箱号/道具；请新建配置");
                }
                if(!remove && old!=null && revision(old).equals(revision(next))) return envelope(old);
                String before,after;
                try {before=json.writeValueAsString(old);after=json.writeValueAsString(next);} catch(Exception e){throw new IllegalStateException(e);}
                long auditId;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO "+AUDIT+" (actor,section_name,operation_name,before_json,after_json,state) VALUES (?,?,?,?,?,'pending')",Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1,actor);p.setString(2,key);p.setString(3,remove?"delete":creating?"create":"update");p.setString(4,before);p.setString(5,after);p.executeUpdate();
                    try(ResultSet r=p.getGeneratedKeys()){r.next();auditId=r.getLong(1);}
                }
                List<Field> writable=s.fields.stream().filter(f->!f.name.equals(s.primaryKey) || creating && !s.autoId).collect(Collectors.toList());
                String sql;
                if(remove) sql="DELETE FROM "+table(s)+" WHERE `"+s.primaryKey+"`=?";
                else if(creating) sql="INSERT INTO "+table(s)+" ("+writable.stream().map(f->"`"+f.name+"`").collect(Collectors.joining(","))+") VALUES ("+writable.stream().map(f->"?").collect(Collectors.joining(","))+")";
                else sql="UPDATE "+table(s)+" SET "+writable.stream().map(f->"`"+f.name+"`=?").collect(Collectors.joining(","))+(s.primaryKey==null?"":" WHERE `"+s.primaryKey+"`=?");
                try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
                    int index=1;
                    if(!remove) for(Field f:writable) p.setObject(index++,next.get(f.name));
                    if(remove || !creating && s.primaryKey!=null) p.setLong(index,id);
                    if(p.executeUpdate()!=1) throw new Failure(409,"配置写入数量异常，请刷新并查看修改记录");
                    if(creating && s.autoId) try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE "+AUDIT+" SET state='applied' WHERE id=?")){p.setLong(1,auditId);p.executeUpdate();}
                return remove?Collections.singletonMap("deleted",true):envelope(rows(c,s,id).get(0));
            } finally {try(Statement st=c.createStatement()){st.execute("UNLOCK TABLES");}}
        } catch(SQLException e) {throw unavailable();}
    }
    public List<Map<String,Object>> history(String key) {
        admin();section(key);
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,actor,operation_name,state,created_at,before_json,after_json FROM "+AUDIT+" WHERE section_name=? ORDER BY id DESC LIMIT 30")) {
            p.setString(1,key);try(ResultSet r=p.executeQuery()) {
                List<Map<String,Object>> out=new ArrayList<>();
                while(r.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(String n:Arrays.asList("id","actor","operation_name","state","created_at","before_json","after_json")) row.put(n,r.getString(n));
                    out.add(row);
                }return out;
            }
        } catch(SQLException e) {if(e.getErrorCode()==1146) return Collections.emptyList();throw unavailable();}
    }
}
