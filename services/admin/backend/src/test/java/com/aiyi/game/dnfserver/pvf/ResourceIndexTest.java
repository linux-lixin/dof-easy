package com.aiyi.game.dnfserver.pvf;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResourceIndexTest {
 @Test public void roundtripKeepsOriginalItemFieldsWithoutComputedSetterConflicts()throws Exception {
  ResourceIndex index=new ResourceIndex();Equipment e=new Equipment();e.setId(123);e.setName("测试");
  index.equipment=Arrays.asList(e);index.stackable=Collections.emptyList();index.unverifiedItems.add(456);
  ResourceIndex restored=ResourceIndex.mapper().readValue(ResourceIndex.mapper().writeValueAsBytes(index),ResourceIndex.class);
  assertEquals(123,restored.equipment.get(0).getId());assertEquals("测试",restored.equipment.get(0).getName());assertTrue(restored.unverifiedItems.contains(456));
 }
}
