package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NativeUiCoordinatesTest {
    private NativeUiState sample() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        Map<String,Object> root=mapper.readValue(PrivateNativeFixtures.open("ui-native-100.json"),new TypeReference<Map<String,Object>>(){});
        return NativeUiState.fromText(mapper.convertValue(root.get("after"),new TypeReference<Map<String,String>>(){}));
    }
    @Test public void namedCoordinateEditPreservesEveryUnrelatedFieldAndSwitch() throws Exception {
        NativeUiState before=sample(); Map<String,Integer> patch=new HashMap<>();patch.put("healthPercentX",250);patch.put("skillBar8Y",537);
        NativeUiState after=NativeUiCoordinates.apply(before,patch);
        NativeHotkeys a=NativeHotkeys.decode(before.text("Hotkeys")),b=NativeHotkeys.decode(after.text("Hotkeys"));
        assertEquals("250",b.text(27));assertEquals("537",b.text(78));assertEquals(a.size(),b.size());
        for(int i=0;i<a.size();i++)if(i!=27 && i!=78)assertArrayEquals(a.raw(i),b.raw(i));
        assertEquals(Collections.singletonList("Hotkeys"),before.changedFields(after));
        assertEquals(12,NativeUiCoordinates.inspect(after).size());
        assertEquals(before.revision(),NativeUiCoordinates.apply(after,new HashMap<String,Integer>(){{put("healthPercentX",248);put("skillBar8Y",535);}}).revision());
    }
    @Test public void unknownOutOfRangeAndEmptyPatchesCannotChangeAnything() throws Exception {
        NativeUiState state=sample();
        for(Map<String,Integer> patch:Arrays.asList(Collections.singletonMap("Hotkeys",1),Collections.singletonMap("healthPercentX",32768),Collections.singletonMap("healthPercentX",-32769),Collections.<String,Integer>emptyMap())){
            try{NativeUiCoordinates.apply(state,patch);fail();}catch(DofFailure e){assertEquals(400,e.status);}
        }
        assertEquals(sample().revision(),state.revision());
    }
}
