package com.aiyi.game.dnfserver.pvf;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.common.Item;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemExplanationTest {
    private Item parse(String json) {
        Item item = new Item();
        item.parseForScript(new JSONObject(json));
        return item;
    }

    @Test public void jianlaiExplainIsIncludedAndSimplified() {
        assertEquals("开启后获得：\n土罐的至臻袖珍罐*10",
            parse("{\"[explain]\":[\"開啟後獲得：\\n土罐的至臻袖珍罐*10\"]}").getExplain());
    }

    @Test public void moreSpecificEquipmentDescriptionKeepsPriority() {
        assertEquals("detail", parse("{\"[detail explain]\":[\"detail\"],\"[basic explain]\":[\"basic\"],\"[explain]\":[\"generic\"]}").getExplain());
        assertEquals("basic", parse("{\"[basic explain]\":[\"basic\"],\"[explain]\":[\"generic\"]}").getExplain());
    }

    @Test public void emptyPreferredDescriptionsFallBack() {
        assertEquals("generic", parse("{\"[detail explain]\":[],\"[basic explain]\":[\"  \"],\"[explain]\":[\"generic\"]}").getExplain());
        assertEquals("generic", parse("{\"[detail explain]\":[null],\"[basic explain]\":null,\"[explain]\":[\"generic\"]}").getExplain());
    }

    @Test public void absentExplanationStaysEmpty() {
        assertEquals("", parse("{}").getExplain());
    }

    @Test public void resourceIndexRoundtripRetainsExplanation() throws Exception {
        Item original = parse("{\"[explain]\":[\"奖品说明\\n仅为资源文本\"]}");
        Item restored = ResourceIndex.mapper().readValue(ResourceIndex.mapper().writeValueAsBytes(original), Item.class);
        assertEquals(original.getExplain(), restored.getExplain());
    }
}
