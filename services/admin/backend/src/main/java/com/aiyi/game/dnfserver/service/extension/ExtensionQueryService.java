package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Fixed projections, never accepts table/column names from HTTP callers. */
@Service
public class ExtensionQueryService {
    @Resource private AccountVODao accountVODao;
    @Resource private DataSource dataSource;
    @org.springframework.beans.factory.annotation.Value("${DNF_COMPANION_PREVIEW:}") private String companionPreview;
    public static class View { public String key,title,table,filter,order; public String database="qk"; public List<String> columns; }
    private final List<View> views;
    public ExtensionQueryService() {
        try {views=new ObjectMapper().readValue(getClass().getResourceAsStream("/extension/records.json"),new TypeReference<List<View>>(){});}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public void requireAdmin() {
        AccountVO user=accountVODao.get(ThreadUtil.getUserId());
        if(user==null || !user.isAdmin()) throw new ActivityConfigService.Failure(403,"仅管理员可以查看运营记录");
    }
    public List<View> catalog(){requireAdmin();return views;}
    public Map<String,Object> records(String key,Long owner,int page,int size){
        requireAdmin();
        if(page<1 || page>10000 || size<1 || size>100 || owner!=null && owner<1)throw new ActivityConfigService.Failure(400,"分页或玩家编号无效");
        View v=views.stream().filter(x->x.key.equals(key)).findFirst().orElseThrow(()->new ActivityConfigService.Failure(404,"未知记录分类"));
        if(owner!=null && v.filter==null)throw new ActivityConfigService.Failure(400,"此记录不支持玩家过滤");
        if(!Arrays.asList("qk","taiwan_cain_log").contains(v.database))throw new ActivityConfigService.Failure(409,"记录来源版本不兼容");
        String where=owner==null?"":" WHERE `"+v.filter+"`=?";
        List<Map<String,Object>> items=new ArrayList<>();
        String columns="`"+String.join("`,`",v.columns)+"`";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT "+columns+" FROM `"+v.database+"`.`"+v.table+"`"+where+" ORDER BY `"+v.order+"` DESC LIMIT ? OFFSET ?")) {
            int i=1;if(owner!=null)p.setLong(i++,owner);p.setInt(i++,size+1);p.setInt(i,(page-1)*size);
            try(ResultSet r=p.executeQuery()) {while(r.next()){Map<String,Object> item=new LinkedHashMap<>();for(String name:v.columns)item.put(name,r.getString(name));items.add(item);}}
        }catch(SQLException e){throw new ActivityConfigService.Failure(409,"记录表未初始化或版本不匹配，已停止查询");}
        Map<String,Object> out=new LinkedHashMap<>();out.put("hasMore",items.size()>size);out.put("rows",items.subList(0,Math.min(size,items.size())));out.put("page",page);out.put("filterField",v.filter);return out;
    }
    public Object companion(){
        requireAdmin();
        if(companionPreview==null||companionPreview.isEmpty())return Collections.emptyList();
        try {
            java.nio.file.Path p=java.nio.file.Paths.get(companionPreview).toAbsolutePath().normalize();
            if(!p.equals(p.toRealPath())||!java.nio.file.Files.isRegularFile(p)||java.nio.file.Files.size(p)>8*1024*1024)
                throw new IllegalArgumentException("Invalid private resource preview");
            try(java.io.InputStream in=java.nio.file.Files.newInputStream(p)){return new ObjectMapper().readValue(in,Object.class);}
        } catch(Exception e){throw new ActivityConfigService.Failure(409,"本机配套资源预览未配置或无法读取");}
    }
    public Object features(){
        requireAdmin();try{return new ObjectMapper().readValue(getClass().getResourceAsStream("/extension/features.json"),Object.class);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public Map<String,Object> clock(){
        requireAdmin();Map<String,Object> out=new LinkedHashMap<>();
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT NOW(),UTC_TIMESTAMP(),@@session.time_zone,@@event_scheduler")){
            r.next();out.put("databaseNow",r.getString(1));out.put("utcNow",r.getString(2));out.put("databaseTimezone",r.getString(3));out.put("eventScheduler",r.getString(4));
        }catch(SQLException e){throw new ActivityConfigService.Failure(503,"数据库状态不可用");}
        out.put("notice","签到/收集箱按数据库 NOW 判断，战令显式转为 +08:00；续期不会自动清空玩家进度。");return out;
    }
}
