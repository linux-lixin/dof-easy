package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** The 110 blood-layout and 22 character-creation fields serialized by native UI Save.
 * Produces candidates only. No database access or claim that general Save has run.
 */
public final class NativeUiLayout {
    private NativeUiLayout() { }

    private static String[][] parse(NativeUiState state) {
        try {
            String raw=state.text("temp_3");
            if (raw==null) throw new IllegalArgumentException();
            String[] sections=raw.split("##",-1);
            if (sections.length<2) throw new IllegalArgumentException();
            String[][] fields=new String[sections.length][];
            for (int i=0;i<sections.length;i++) fields[i]=sections[i].split("&",-1);
            for (int s=0;s<2;s++) {
                int required=s==0?110:22;
                if (fields[s].length<required) throw new IllegalArgumentException();
                for (int i=0;i<required;i++) {
                    if(s==0 && i==40) spacing(fields[s][i]);
                    else {
                        if (!fields[s][i].matches("-?[0-9]{1,10}")) throw new IllegalArgumentException();
                        Integer.parseInt(fields[s][i]);
                    }
                }
            }
            return fields;
        } catch (IllegalArgumentException e) {
            throw new DofFailure(409,"原生界面布局缺失、格式异常或超出整数范围，停止修改");
        }
    }
    private static BigDecimal spacing(String text) {
        if(text==null || !text.matches("-?[0-9]{1,10}(?:\\.[0-9]{1,8})?"))throw new IllegalArgumentException();
        BigDecimal number=new BigDecimal(text);
        if(number.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE))<0 || number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0)throw new IllegalArgumentException();
        return number;
    }

    public static Map<String,Number> read(NativeUiState state) {
        String[][] fields=parse(state);
        Map<String,Number> result=new LinkedHashMap<>();
        for (int s=0;s<2;s++) for (int i=0;i<(s==0?110:22);i++)
            result.put((s==0?"blood.":"create.")+i,fields[s][i].contains(".")?spacing(fields[s][i]):Integer.valueOf(fields[s][i]));
        return Collections.unmodifiableMap(result);
    }

    public static NativeUiState candidate(NativeUiState state,Map<String,Integer> changes) {
        Map<String,BigDecimal> values=new LinkedHashMap<>();
        if(changes!=null)for(Map.Entry<String,Integer> item:changes.entrySet())values.put(item.getKey(),item.getValue()==null?null:BigDecimal.valueOf(item.getValue()));
        return edit(state,values);
    }
    public static NativeUiState edit(NativeUiState state,Map<String,BigDecimal> changes) {
        if (changes==null || changes.isEmpty() || changes.size()>132)
            throw new DofFailure(400,"请提供有效的界面布局修改");
        String[][] fields=parse(state);
        for (Map.Entry<String,BigDecimal> entry:changes.entrySet()) {
            String key=entry.getKey();
            if (key==null || !key.matches("(?:blood|create)\\.(?:0|[1-9][0-9]{0,2})") || entry.getValue()==null)
                throw new DofFailure(400,"界面布局字段或数值无效");
            int section=key.startsWith("blood.")?0:1;
            int index=Integer.parseInt(key.substring(key.indexOf('.')+1));
            if (index>=(section==0?110:22)) throw new DofFailure(400,"界面布局字段超出配套版本范围");
            // Preserve the original spelling for semantic no-ops and every extension.
            BigDecimal value=entry.getValue();
            try {
                if(section==0 && index==40)spacing(value.toPlainString());
                else value.intValueExact();
            } catch(IllegalArgumentException | ArithmeticException e) {throw new DofFailure(400,"布局坐标必须是范围内的整数；栏位间隔可使用最多八位小数");}
            if (new BigDecimal(fields[section][index]).compareTo(value)!=0)
                fields[section][index]=value.stripTrailingZeros().toPlainString();
        }
        String[] sections=new String[fields.length];
        for (int i=0;i<fields.length;i++) sections[i]=String.join("&",fields[i]);
        Map<String,byte[]> result=new LinkedHashMap<>();
        for (String key:NativeUiState.COLUMNS) result.put(key,state.get(key));
        result.put("temp_3",String.join("##",sections).getBytes(StandardCharsets.UTF_8));
        return new NativeUiState(result);
    }
}
