package com.aiyi.game.dnfserver.pvf;

import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline-only PVF conversion; runtime checks the index against the actual source. */
public class ResourceIndex {
    public int version = 1;
    public String sha256;
    public long size;
    public int invalidNameLines, duplicateNameKeys;
    public Set<Integer> unverifiedItems = new LinkedHashSet<>();
    public List<Equipment> equipment;
    public List<Stackable> stackable;
    public Map<String,Set<String>> references = new LinkedHashMap<>();
    public static ObjectMapper mapper() {
        return new ObjectMapper().setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE).setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
    }
    public static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);
        }
        StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format("%02x",b));return value.toString();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Output index file required");
        if(System.getenv("DNF_PVF_INDEX")!=null)throw new IllegalStateException("Offline builder must read PVF directly");
        PvfManager manager=new PvfManager();ResourceIndex index=new ResourceIndex();
        File source=manager.sourceFile();index.sha256=hash(source);index.size=source.length();
        for(String path:Arrays.asList("equipment/equipment.lst","stackable/stackable.lst","dungeon/dungeon.lst","npc/npc.lst")) {
            cn.hutool.json.JSONObject entries=manager.getPvf().getScript(path);
            if(entries==null)throw new IllegalStateException("Missing required list: "+path);
            index.references.put(path,new LinkedHashSet<>(entries.keySet()));
        }
        index.equipment=manager.getEquipmentList();index.stackable=manager.getStackableList();
        index.unverifiedItems=manager.unverifiedItems;
        index.invalidNameLines=com.xiaoyouma.dnf.parser.pvf.parser.StrParser.invalidLines();
        index.duplicateNameKeys=com.xiaoyouma.dnf.parser.pvf.parser.StrParser.duplicateKeys();
        if(!index.sha256.equals(hash(source)))throw new IllegalStateException("PVF changed during conversion");
        Path target=Paths.get(args[0]),tmp=target.resolveSibling(target.getFileName()+".tmp");
        mapper().writeValue(tmp.toFile(),index);
        Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        System.out.println("INDEX_BUILT equipment="+index.equipment.size()+" stackable="+index.stackable.size()+" nameWarnings="+(index.invalidNameLines+index.duplicateNameKeys));
    }
}
