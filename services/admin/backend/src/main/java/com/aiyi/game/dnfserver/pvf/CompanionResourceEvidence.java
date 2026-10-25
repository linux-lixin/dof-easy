package com.aiyi.game.dnfserver.pvf;

import cn.hutool.json.JSONObject;
import java.io.File;
import java.util.*;

/** Offline inspection of fixed companion paths. Never changes or exports PVF contents. */
public class CompanionResourceEvidence {
 public static void main(String[] args)throws Exception {
  if(args.length!=1 || System.getenv("DNF_PVF_INDEX")!=null)throw new IllegalArgumentException("Offline output file required; index environment must be unset");
  PvfManager manager=new PvfManager();File source=manager.sourceFile();
  String hash=ResourceIndex.hash(source);
  Map<String,List<String>> targets=new LinkedHashMap<>();
  targets.put("etc/mercenary.etc",Collections.emptyList());
  targets.put("etc/newcashshop.etc",Arrays.asList("creature","mileage item","mileage\u00a0item"));
  targets.put("character/expertjob/doll_controller.exj",Arrays.asList("shop doll info"));
  List<Map<String,Object>> rows=new ArrayList<>();
  for(Map.Entry<String,List<String>> entry:targets.entrySet()){
   Map<String,Object> row=new LinkedHashMap<>();row.put("path",entry.getKey());
   try {
    JSONObject data=manager.getPvf().getScript(entry.getKey());
    row.put("status",data==null?"unverified":"parsed");
    Map<String,Boolean> sections=new LinkedHashMap<>();
    for(String key:entry.getValue())sections.put(key,data!=null&&(data.containsKey(key)||data.containsKey("["+key+"]")));
    row.put("sections",sections);
    row.put("topLevelKeyCount",data==null?0:data.size());
   }catch(Exception e){row.put("status","unverified");row.put("reason","source_or_parser_unavailable");}
   rows.add(row);
  }
  if(!hash.equals(ResourceIndex.hash(source)))throw new IllegalStateException("Source changed during inspection");
  Map<String,Object> out=new LinkedHashMap<>();out.put("pvfSha256",hash);out.put("gameplayVerified",false);out.put("resources",rows);
  File target=new File(args[0]);if(target.exists())throw new IllegalArgumentException("Output already exists");
  ResourceIndex.mapper().writeValue(target,out);
 }
}
