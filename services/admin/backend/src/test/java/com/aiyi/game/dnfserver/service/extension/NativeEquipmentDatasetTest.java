package com.aiyi.game.dnfserver.service.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class NativeEquipmentDatasetTest {
    private final ObjectMapper json=new ObjectMapper();
    private NativeEquipmentDataset.Spec spec(String key)throws Exception{
        ActivityConfigService.Section[] all;try(InputStream in=getClass().getResourceAsStream("/extension/activities.json")){all=json.readValue(in,ActivityConfigService.Section[].class);}
        return new NativeEquipmentDataset.Spec(Arrays.stream(all).filter(s->s.key.equals(key)).findFirst().get(),"dataset-test","11111111-1111-4111-8111-111111111111");
    }
    private Map<String,Object> row(long id){Map<String,Object> r=new LinkedHashMap<>();r.put("code",id);r.put("restitution","旧扩展/0003037");r.put("Return_quantity","opaque/01");r.put("differ_lv",5L);return r;}
    private byte[] encode(List<Map<String,Object>> rows)throws Exception{Iterator<Map<String,Object>> it=rows.iterator();ByteArrayOutputStream out=new ByteArrayOutputStream();NativeEquipmentDataset.write(spec("inheritance"),()->it.hasNext()?it.next():null,out);return out.toByteArray();}
    private void reject(int status,byte[] bytes)throws Exception{try{NativeEquipmentDataset.inspect(spec("inheritance"),new ByteArrayInputStream(bytes));fail();}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}}
    @Test public void fullStreamRoundTripsMoreThanOneThousandRowsIncludingZeroAndOpaqueText()throws Exception{
        List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<1005;i++)rows.add(row(i));byte[] bytes=encode(rows);Map<String,Object> result=NativeEquipmentDataset.inspect(spec("inheritance"),new ByteArrayInputStream(bytes));assertEquals(1005L,result.get("rowCount"));assertEquals(true,result.get("integrityVerified"));assertEquals(false,result.get("nativeWrites"));assertEquals(false,result.get("writePlanCreated"));
    }
    @Test public void hashIgnoresObjectKeyOrderButDetectsChangedRowsTruncationAndDuplicateCodes()throws Exception{
        byte[] bytes=encode(Collections.singletonList(row(0)));Map<String,Object> data=json.readValue(bytes,Map.class);Map<String,Object> reversed=new LinkedHashMap<>();List<String> keys=new ArrayList<>(data.keySet());Collections.reverse(keys);for(String key:keys)reversed.put(key,data.get(key));NativeEquipmentDataset.inspect(spec("inheritance"),new ByteArrayInputStream(json.writeValueAsBytes(reversed)));
        reject(409,new String(bytes,StandardCharsets.UTF_8).replace("opaque/01","opaque/02").getBytes(StandardCharsets.UTF_8));reject(400,Arrays.copyOf(bytes,bytes.length-2));
        try{encode(Arrays.asList(row(1),row(1)));fail();}catch(ActivityConfigService.Failure e){assertEquals(400,e.status);}
    }
    @Test public void wrongBindingSchemaUnknownFieldsAndExtraObjectsAreNeverAccepted()throws Exception{
        byte[] bytes=encode(Collections.singletonList(row(0)));String text=new String(bytes,StandardCharsets.UTF_8);
        reject(409,text.replace("dataset-test","other-test").getBytes(StandardCharsets.UTF_8));reject(409,text.replace("inheritance","cross-border").getBytes(StandardCharsets.UTF_8));reject(400,(text+"{}").getBytes(StandardCharsets.UTF_8));reject(400,text.replace("\"schema\":1","\"schema\":1,\"schema\":1").getBytes(StandardCharsets.UTF_8));reject(400,text.replace("\"code\":0","\"command\":\"arbitrary\",\"code\":0").getBytes(StandardCharsets.UTF_8));
    }
    @Test public void unsignedValuesAndSupplementaryCharactersStayExactWithoutTreatingThemAsWritableRules()throws Exception{
        Map<String,Object> value=row(4294967295L);value.put("restitution","\ud83d\ude00/legacy");byte[] bytes=encode(Collections.singletonList(value));Map<String,Object> result=NativeEquipmentDataset.inspect(spec("inheritance"),new ByteArrayInputStream(bytes));assertEquals(1L,result.get("rowCount"));assertEquals(false,result.get("writePlanCreated"));
    }
    @Test public void oversizedScalarsAndNestedRowsAreRejectedBeforeUnboundedAllocation()throws Exception{
        String huge="{\"format\":\""+String.join("",Collections.nCopies(5000,"a"))+"\"}";reject(400,huge.getBytes(StandardCharsets.UTF_8));
        String data=new String(encode(Collections.singletonList(row(0))),StandardCharsets.UTF_8);reject(400,data.replace("\"code\":0","\"code\":[[[[0]]]]").getBytes(StandardCharsets.UTF_8));reject(400,data.replace("\"code\":0","\"code\":0.1").getBytes(StandardCharsets.UTF_8));
    }
}
