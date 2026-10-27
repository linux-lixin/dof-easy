package com.aiyi.game.dnfserver.pvf;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.entity.stackable.StackableType;
import com.xiaoyouma.dnf.parser.pvf.coder.PvfCoder;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import com.xiaoyouma.dnf.parser.pvf.model.PvfFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author xiatian
 */
@Component
public class PvfManager {

    Logger logger = LoggerFactory.getLogger(PvfManager.class);

    public final java.util.Set<Integer> unverifiedItems = new java.util.LinkedHashSet<>();
    public int unverifiedItemCount(){ResourceIndex data=index();return data==null?unverifiedItems.size():data.unverifiedItems.size();}
    private boolean initialized;
    private ResourceIndex index;
    private long indexedMtime;
    private synchronized ResourceIndex index() {
        String path=System.getenv("DNF_PVF_INDEX");
        if(path==null || path.isEmpty())return null;
        File source=sourceFile();
        if(index!=null && indexedMtime==source.lastModified() && index.size==source.length())return index;
        try {
            ResourceIndex candidate=ResourceIndex.mapper().readValue(new File(path),ResourceIndex.class);
            if(candidate.version!=1 || candidate.size!=source.length() || !candidate.sha256.equals(ResourceIndex.hash(source)))throw new IllegalStateException("资源索引与游戏 PVF 不匹配");
            index=candidate;indexedMtime=source.lastModified();return index;
        } catch(Exception e) {throw new IllegalStateException("请先为当前游戏 PVF 构建资源索引",e);}
    }
    public void validateIndex() {index();}
    /** Fresh validation for irreversible attachment issuance; bypass mtime caches. */
    public static class VerifiedMailItem {
        public com.aiyi.game.dnfserver.entity.common.Item item;
        public String kind,sha256;
    }
    public VerifiedMailItem verifiedMailItem(int id)throws Exception {
        String path=System.getenv("DNF_PVF_INDEX");
        if(path==null||path.isEmpty())throw new IllegalStateException("邮件附件须绑定经校验的PVF索引");
        File source=sourceFile();String hash=ResourceIndex.hash(source);
        ResourceIndex data=ResourceIndex.mapper().readValue(new File(path),ResourceIndex.class);
        if(data.version!=1||data.size!=source.length()||!hash.equals(data.sha256)||data.equipment==null||data.stackable==null||data.unverifiedItems==null||data.unverifiedItems.contains(id))throw new IllegalStateException("邮件索引与绑定PVF不匹配或物品未核实");
        VerifiedMailItem result=new VerifiedMailItem();result.sha256=hash;
        for(Equipment value:data.equipment)if(value.getId()==id){if(result.item!=null)throw new IllegalStateException("物品编号重复");result.item=value;result.kind="equipment";}
        for(Stackable value:data.stackable)if(value.getId()==id){if(result.item!=null)throw new IllegalStateException("物品编号重复");result.item=value;result.kind="stackable";}
        if(result.item==null||!hash.equals(ResourceIndex.hash(source)))throw new IllegalStateException("物品不存在或PVF检查期间改变");
        return result;
    }
    public boolean referenceExists(String path,String id) {
        ResourceIndex data=index();
        if(data!=null) {
            if(!data.references.containsKey(path))throw new IllegalStateException("资源类型尚未建立索引");
            return data.references.get(path).contains(id);
        }
        JSONObject list=getPvf().getScript(path);
        if(list==null)throw new IllegalStateException("资源列表不存在");
        return list.containsKey(id);
    }
    public int invalidNameLines() {ResourceIndex data=index();return data==null?com.xiaoyouma.dnf.parser.pvf.parser.StrParser.invalidLines():data.invalidNameLines;}
    public int duplicateNameKeys() {ResourceIndex data=index();return data==null?com.xiaoyouma.dnf.parser.pvf.parser.StrParser.duplicateKeys():data.duplicateNameKeys;}

    public File sourceFile() {
        String configured = System.getenv("DNF_PVF_PATH");
        return new File(configured == null || configured.trim().isEmpty() ? "data/Script.pvf" : configured);
    }
    public boolean managedSource() { return System.getenv("DNF_PVF_PATH") != null; }
    public synchronized void init() {
        File file = sourceFile();
        if (!file.isFile() || !file.canRead()) throw new IllegalStateException("配套 PVF 未挂载或不可读");
        com.xiaoyouma.dnf.parser.pvf.parser.StrParser.resetDiagnostics();
        PvfCoder.initialize(file.getAbsolutePath(), Charset.forName("Big5"));
        initialized = true;
    }
    public synchronized Pvf getPvf() {
        if (!initialized) init();
        return PvfCoder.getPvf();
    }

    /**
     * 获取装备列表
     */
    public List<Equipment> getEquipmentList(){
        ResourceIndex data=index();if(data!=null)return data.equipment;
        Pvf pvf = getPvf();
        List<Equipment> equipmentList = new ArrayList<>();
        JSONObject script = pvf.getScript("equipment/equipment.lst");
        for (String key : script.keySet()) {
            try {
            String str = script.getStr(key);
            if (str.startsWith("/")){
                str = str.substring(1);
            }
            JSONObject equipmentScript = pvf.getScript("equipment/" + str);
            Equipment equipment = Equipment.forScript(equipmentScript);
            equipment.setId(Integer.parseInt(key));
            if ("未命名".equals(equipment.getName()) || equipment.getName().contains("找不到代码") || equipment.getName().trim().isEmpty()){
                unverifiedItems.add(Integer.parseInt(key));
                continue;
            }
            equipmentList.add(equipment);
            } catch (RuntimeException e) {unverifiedItems.add(Integer.parseInt(key));}
        }
        return equipmentList;
    }

    /**
     * 获取道具列表
     */
    public List<Stackable> getStackableList(){
        ResourceIndex data=index();if(data!=null)return data.stackable;
        Pvf pvf = getPvf();
        List<Stackable> stackableList = new ArrayList<>();
        JSONObject script = pvf.getScript("stackable/stackable.lst");
        for (String key : script.keySet()) {
            try {
            String str = script.getStr(key);
            String path = "stackable/" + str;
            if (str.startsWith("/")){
                path = "stackable" + str;
            }
            JSONObject stackableScript = pvf.getScript(path);
            Stackable stackable = Stackable.forScript(stackableScript);
            stackable.setId(Integer.parseInt(key));
            if ("未命名".equals(stackable.getName()) || stackable.getName().contains("找不到代码") || stackable.getName().trim().isEmpty()){
                unverifiedItems.add(Integer.parseInt(key));
                continue;
            }
            stackableList.add(stackable);
            } catch (RuntimeException e) {unverifiedItems.add(Integer.parseInt(key));}
        }
        return stackableList;
    }
}
