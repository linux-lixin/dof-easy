package com.aiyi.game.dnfserver.controller;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.extension.*;
import cn.hutool.json.JSONObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.annotation.Resource;
import java.util.*;
@RestController
@RequestMapping("api/v1/admin/extension/resources")
public class ExtensionResourceController {
 @Resource private ExtensionQueryService security;
 @Resource private PvfManager pvf;
 public static class Query { public String kind; public List<Integer> ids; }
 @PostMapping("validate") public Object validate(@RequestBody Query query){
  security.requireAdmin();
  Map<String,List<String>> paths=new HashMap<>();paths.put("item",Arrays.asList("equipment/equipment.lst","stackable/stackable.lst"));paths.put("dungeon",Arrays.asList("dungeon/dungeon.lst"));paths.put("npc",Arrays.asList("npc/npc.lst"));
  if(query==null || !paths.containsKey(query.kind) || query.ids==null || query.ids.size()>200 || query.ids.isEmpty() || query.ids.stream().anyMatch(x->x==null||x<1))throw new ActivityConfigService.Failure(400,"资源类型或编号无效，每次最多 200 项");
  List<Map<String,Object>> out=new ArrayList<>();
  for(Integer id:new LinkedHashSet<>(query.ids)){
   boolean complete=true,found=false;
   for(String path:paths.get(query.kind))try{if(pvf.referenceExists(path,id.toString()))found=true;}catch(Exception e){complete=false;}
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("status",found?"found":complete?"missing":"unverified");out.add(row);
  }
  return out;
 }
 @ExceptionHandler(ActivityConfigService.Failure.class) public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
}
