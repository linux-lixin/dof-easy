package com.aiyi.game.dnfserver.service.dof;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Native record projections, bound to the same verified instance as configuration. */
@Service
public class DofRecordsService {
    @Resource private DofAdminService access;
    private static final class View {
        final String key, title, table, owner, order;
        final String[] columns, labels;
        String date, ownerLabel="玩家编号";
        String database="dof_native";
        final Set<String> search = new LinkedHashSet<>();
        View(String key, String title, String table, String owner, String order, String columns, String labels) {
            this.key=key; this.title=title; this.table=table; this.owner=owner; this.order=order;
            this.columns=columns.split(","); this.labels=labels.split(",");
            if (this.columns.length != this.labels.length) throw new IllegalArgumentException("Record label mismatch");
        }
        View filter(String date,String ownerLabel,String... search) {
            this.date=date;this.ownerLabel=ownerLabel;this.search.addAll(Arrays.asList(search));
            if(!Arrays.asList(columns).containsAll(this.search))throw new IllegalArgumentException("Unknown search field");
            return this;
        }
    }
    private static final List<View> VIEWS = catalogViews(Arrays.asList(
        new View("signin-users", "签到进度", "sign_in_users", "user_id", "user_id",
            "user_id,current_checkins,end_date,last_checkin_date", "玩家编号,当前签到次数,活动截止日期,最近签到日期"),
        new View("signin-checkins", "签到记录", "sign_in_checkin_records", "user_id", "checkin_date",
            "user_id,checkin_date,is_double", "玩家编号,签到日期,双倍标记").filter("checkin_date","玩家编号"),
        new View("signin-storage", "签到奖励状态", "sign_in_storage", "UID", "UID",
            "UID,num,Cumulative_1,Cumulative_2,Cumulative_3,Cumulative_4,d_num",
            "玩家编号,签到次数,累计奖励一状态,累计奖励二状态,累计奖励三状态,累计奖励四状态,双倍次数"),
        new View("shop-purchases", "神秘商店购买记录", "user_mystery_shop_purchase", "user_id", "id",
            "id,user_id,account_id,cid,item_id,purchase_time,restricted_type,purchase_count,npc_id",
            "记录编号,玩家编号,账号编号,角色编号,道具编号,购买时间,限制类型,购买次数,NPC编号"),
        new View("cdk-claims", "固定 CDK 兑换记录", "cdk_solid_stat", "m_id", "id",
            "id,cdk_id,m_id,cid", "记录编号,兑换码配置编号,账号编号,角色编号").filter(null,"账号编号"),
        new View("transactions", "交易记录", "transaction", "UID", "ITime",
            "ID,UID,accname,code,codenum,MING,ITime", "记录编号,账号编号,账号名称,道具编号,道具数量,记录说明,交易时间")
            .filter("ITime","账号编号","accname","MING"),
        new View("dungeon-runs", "刷图与通关记录", "map_history", "uid", "leaveTime",
            "ID,uid,charactName,dungeonId,dungeonName,difficulty,mapCount,killMobCount,useCubepieceClearCount,hell,passingTime,clearInfo,success,leaveTime,reward_state,is_captain,is_team,c_id",
            "记录编号,账号编号,角色名称,地下城编号,地下城名称,难度,地图数量,击杀数量,无色消耗次数,深渊标记,通关用时原值,通关信息,通关状态,离开时间,原生公告状态,队长标记,原生队伍人数,角色编号")
            .filter("leaveTime","账号编号","charactName","dungeonName"),
        new View("power-settlements", "战力排行结算日志", "game_charts_logs", null, "id",
            "id,charts_day,logs", "记录编号,结算时间,结算记录").filter("charts_day",null,"logs"),
        new View("power-worship", "战力排行膜拜记录", "game_charts_mobai", "cid", "id",
            "id,cid,mobai_time,mobai_cid", "记录编号,发起角色编号,膜拜时间,目标角色编号").filter("mobai_time","发起角色编号"),
        new View("race-settlements", "竞速排行结算日志", "game_tongguan_logs", null, "id",
            "id,charts_day,logs", "记录编号,结算时间,结算记录").filter("charts_day",null,"logs"),
        new View("gift-history", "站街与定时奖励记录", "gift_history", null, "id",
            "id,task_id,create_time,content", "记录编号,活动编号,记录时间,中奖记录").filter(null,null,"task_id","content"),
        new View("auction-transactions", "拍卖交易记录", "auction_house_log", "owner_id", "transaction_time",
            "auction_id,owner_id,owner_name,buyer_id,buyer_name,transaction_time,item_id,item_name,item_cnt,unit_price,price,gold_type,item_type,unit_itme_name,seal_flag,add_info,upgrade,endurance,seperate_upgrade,AmplifierType,enchantbead,SealCount,equ_id",
            "拍卖编号,卖方编号,卖方名称,买方编号,买方名称,交易时间,道具编号,道具名称,道具数量,单价,价格,货币类型原值,道具类型原值,计价道具名称,封装标记,附加信息,强化值,耐久值,锻造值,增幅类型,附魔编号,封装次数,装备编号")
            .filter("transaction_time","卖方编号","owner_name","buyer_name","item_name"),
        new View("cheat-reports", "封挂行为记录", "Listofcheats", "UID", "ID",
            "ID,UID,Name,QQ,behavior,Time", "记录编号,账号编号,名称,QQ,行为记录,记录时间").filter(null,"账号编号","Name","behavior"),
        new View("illegal-clients", "客户端违规记录", "qfillegal", "IUID", "ITime",
            "CID,IUID,IUsername,IFile_md5,IMac_md5,MING,IKey_word,IIPAddress,ITime",
            "角色编号,账号编号,账号名称,文件校验值,设备校验值,名称,匹配关键词,连接地址,记录时间")
            .filter(null,"账号编号","IUsername","MING","IKey_word")
    ));
    /** Reuse audited, fixed read projections; HTTP never chooses a database or table. */
    private static List<View> catalogViews(List<View> primary) {
        List<View> result=new ArrayList<>(primary);Set<String> keys=new HashSet<>(),tables=new HashSet<>();
        for(View v:primary){keys.add(v.key);tables.add(v.database+"."+v.table);}
        try(java.io.InputStream in=DofRecordsService.class.getResourceAsStream("/extension/records.json")) {
            List<Map<String,Object>> extra=new ObjectMapper().readValue(in,new TypeReference<List<Map<String,Object>>>(){});
            for(Map<String,Object> row:extra) {
                String key=(String)row.get("key"),table=(String)row.get("table"),database=(String)row.getOrDefault("database","dof_native");
                String owner=(String)row.get("filter"),order=(String)row.get("order");
                @SuppressWarnings("unchecked") List<String> columns=(List<String>)row.get("columns");
                if(!Arrays.asList("dof_native","taiwan_cain_log").contains(database)
                    || key==null || !key.matches("[a-z][a-z0-9-]*") || table==null || !table.matches("[A-Za-z_][A-Za-z0-9_]*")
                    || order==null || columns==null || columns.isEmpty() || new HashSet<>(columns).size()!=columns.size()
                    || !columns.contains(order) || owner!=null&&!columns.contains(owner)
                    || columns.stream().anyMatch(c->c==null || !c.matches("[A-Za-z_][A-Za-z0-9_]*")))
                    throw new IllegalArgumentException("Invalid fixed record projection");
                if(keys.contains(key) || tables.contains(database+"."+table))continue;
                View v=new View(key,(String)row.get("title"),table,owner,order,String.join(",",columns),String.join(",",columns));
                v.database=database;
                if(Arrays.asList("CID","cid","charac_no").contains(owner))v.ownerLabel="角色编号";
                else if(owner!=null)v.ownerLabel="账号编号";
                if(key.equals("chat-reports"))v.date="occ_time";
                result.add(v);keys.add(key);tables.add(database+"."+table);
            }
        }catch(java.io.IOException | RuntimeException e){throw new IllegalStateException("原生记录固定清单无法加载",e);}
        return Collections.unmodifiableList(result);
    }
    private View view(String key) {
        return VIEWS.stream().filter(v -> v.key.equals(key)).findFirst()
            .orElseThrow(() -> new DofFailure(404, "未知原生记录分类"));
    }
    private Map<String,Object> description(View v) {
        Map<String,Object> out=new LinkedHashMap<>(); out.put("key",v.key); out.put("title",v.title);
        out.put("ownerLabel",v.ownerLabel);out.put("ownerAvailable",v.owner!=null);out.put("periodAvailable",v.date!=null);
        List<Map<String,String>> columns=new ArrayList<>();
        for(int i=0;i<v.columns.length;i++) { Map<String,String> f=new LinkedHashMap<>(); f.put("key",v.columns[i]); f.put("label",v.labels[i]); columns.add(f); }
        List<Object> searches=new ArrayList<>(); for(Map<String,String> f:columns)if(v.search.contains(f.get("key")))searches.add(f);
        out.put("searchFields",searches);out.put("columns",columns); out.put("nativeEquivalence", "pending"); return out;
    }
    private Map<String,Map<String,String>> schema(Connection c)throws SQLException {
        Map<String,Map<String,String>> tables=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement("SELECT TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA IN ('dof_native','taiwan_cain_log')")) {
            p.setQueryTimeout(10);
            try(ResultSet r=p.executeQuery()) {
                while(r.next())tables.computeIfAbsent(r.getString(1)+"."+r.getString(2),k->new LinkedHashMap<>()).put(r.getString(3).toLowerCase(Locale.ROOT),r.getString(4));
            }
        }
        return tables;
    }
    private Map<String,Object> description(View v,Map<String,Map<String,String>> schema) {
        Map<String,Object> out=description(v);Map<String,String> actual=schema.get(v.database+"."+v.table);List<String> missing=new ArrayList<>();
        for(String column:v.columns)if(actual==null || !actual.containsKey(column.toLowerCase(Locale.ROOT)))missing.add(column);
        out.put("available",missing.isEmpty());out.put("missingColumns",missing);
        if(!missing.isEmpty())out.put("reason",actual==null?"当前实例没有记录表 "+v.table:"记录表 "+v.table+" 缺少字段："+String.join("、",missing));
        out.put("table",v.table);out.put("database",v.database);return out;
    }
    public Object catalog() {
        access.admin();
        try(Connection c=access.boundConnection()) {
            Map<String,Object> out=access.context(); List<Object> views=new ArrayList<>();Map<String,Map<String,String>> schema=schema(c);
            for(View v:VIEWS) views.add(description(v,schema)); out.put("views", views); return out;
        } catch(SQLException e) { throw new DofFailure(503,"隔离数据库不可用，未读取记录"); }
    }
    public Object diagnose(String key) {
        access.admin();View v=view(key);
        try(Connection c=access.boundConnection()) {
            Map<String,Map<String,String>> schema=schema(c);Map<String,Object> out=access.context();
            out.put("view",description(v,schema));out.put("tablePresent",schema.containsKey(v.database+"."+v.table));
            List<Map<String,Object>> fields=new ArrayList<>();Map<String,String> actual=schema.getOrDefault(v.database+"."+v.table,Collections.emptyMap());
            for(String column:v.columns) {
                Map<String,Object> field=new LinkedHashMap<>();field.put("key",column);field.put("present",actual.containsKey(column.toLowerCase(Locale.ROOT)));
                field.put("dataType",actual.get(column.toLowerCase(Locale.ROOT)));fields.add(field);
            }
            out.put("fields",fields);out.put("observedAt",java.time.Instant.now().toString());
            try(Statement s=c.createStatement()){s.setQueryTimeout(10);try(ResultSet r=s.executeQuery("SELECT NOW(),@@session.time_zone,@@system_time_zone")) {
                if(r.next()){out.put("databaseNow",r.getString(1));out.put("databaseTimezone",r.getString(2));out.put("systemTimezone",r.getString(3));}
            }}
            out.put("notice","仅检查绑定实例的固定表结构及数据库时间；不读取玩家行，不初始化或修改任何表。字段齐全仍需核对查询权限及实际读取。");return out;
        }catch(SQLException e){throw new DofFailure(503,"记录结构诊断暂不可用，请核对数据库连接、元数据读取权限或超时");}
    }
    public Object records(String key, Long owner, int page, int size) {
        return records(key,owner,page,size,"all",null,null);
    }
    public Object records(String key, Long owner, int page, int size,String period,String searchField,String searchText) {
        access.admin(); View v=view(key);
        if(page<1 || page>100000 || size<1 || size>100 || owner!=null && owner<1)
            throw new DofFailure(400,"页码、每页数量或玩家编号无效");
        if(owner!=null && v.owner==null)throw new DofFailure(400,"此类日志不支持玩家编号筛选");
        if(!Arrays.asList("all","today","yesterday","last7days").contains(period) || !"all".equals(period)&&v.date==null)
            throw new DofFailure(400,"此类记录的时间筛选无效");
        boolean searching=searchText!=null&&!searchText.isEmpty();
        if(searching && (searchText.length()>128 || searchText.indexOf(0)>=0 || !v.search.contains(searchField))
            || !searching && searchField!=null&&!searchField.isEmpty())throw new DofFailure(400,"请选择查询字段并填写有效查询内容");
        List<String> conditions=new ArrayList<>();
        if(owner!=null)conditions.add("`"+v.owner+"`=?");
        if(!"all".equals(period)) {
            String day="today".equals(period)?"CURDATE()":"DATE_SUB(CURDATE(),INTERVAL "+("yesterday".equals(period)?"1":"6")+" DAY)";
            conditions.add("`"+v.date+"` >= "+day);
            conditions.add("`"+v.date+"` < "+("yesterday".equals(period)?"CURDATE()":"DATE_ADD(CURDATE(),INTERVAL 1 DAY)"));
        }
        if(searching)conditions.add("`"+searchField+"` LIKE ? ESCAPE '!'");
        String where=conditions.isEmpty()?"":" WHERE "+String.join(" AND ",conditions);
        StringBuilder order=new StringBuilder("`"+v.order+"` DESC");
        // Tie-break on every projected value, rather than an unspecified physical row order.
        for(String column:v.columns) if(!column.equals(v.order)) order.append(",BINARY `").append(column).append("` DESC");
        String query="SELECT `"+String.join("`,`",v.columns)+"` FROM `"+v.database+"`.`"+v.table+"`"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?";
        try(Connection c=access.boundConnection()) {
            Map<String,Object> available=description(v,schema(c));
            if(!Boolean.TRUE.equals(available.get("available")))throw new DofFailure(409,available.get("reason")+"。未创建或改写原生表；可查询其他已有记录分类。");
            try(PreparedStatement p=c.prepareStatement(query)) {
            p.setQueryTimeout(10); int n=1; if(owner!=null)p.setLong(n++,owner);
            if(searching)p.setString(n++,"%"+searchText.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            p.setInt(n++,size+1); p.setLong(n,(long)(page-1)*size);
            List<Map<String,Object>> rows=new ArrayList<>();
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) { Map<String,Object> row=new LinkedHashMap<>();
                    for(String column:v.columns) row.put(column,r.getString(column)); rows.add(row); }
            }
            Map<String,Object> out=access.context(); out.put("view",description(v)); out.put("page",page); out.put("size",size);
            out.put("hasMore",rows.size()>size); out.put("rows",new ArrayList<>(rows.subList(0,Math.min(size,rows.size()))));
            out.put("observedAt",java.time.Instant.now().toString()); out.put("consistency","live");
            out.put("notice","直接查询原生记录。日期按数据库服务器时区筛选，文字按字面内容包含匹配。游戏写入时翻页结果可能变化；这里不会补发奖励或改写领取状态。");
            return out;
            }
        } catch(SQLException e) {
            if("42S02".equals(e.getSQLState()) || "42S22".equals(e.getSQLState()))
                throw new DofFailure(409,"当前实例的记录结构不兼容，未初始化或覆盖原生表");
            throw new DofFailure(503,"原生记录暂时不可查询；请核对数据库连接、权限或查询超时");
        }
    }
}
