package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.DatabaseToolsController;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class DatabaseToolsRetirementHttpTest {
    @Test public void legacyReadAndWriteSqlAreGoneWithoutAnyDatabaseDependency() throws Exception {
        // No DAO or DataSource is injected: every payload must terminate here.
        MockMvc http = MockMvcBuilders.standaloneSetup(new DatabaseToolsController()).build();
        for (int type : new int[]{0, 1}) {
            String secret = "private_sql_marker_64792";
            String sql = type == 0 ? "SELECT '" + secret + "'" : "DELETE FROM accounts /* " + secret + " */";
            String encoded = Base64.getEncoder().encodeToString(sql.getBytes(StandardCharsets.UTF_8));
            String result = http.perform(post("/api/v1/database").contentType("application/json")
                    .content("{\"type\":" + type + ",\"script\":\"" + encoded + "\"}"))
                    .andExpect(status().isGone()).andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(result.contains(secret));
            assertFalse(result.contains(encoded));
        }
        http.perform(post("/api/v1/database").contentType("application/json")
                .content("{\"type\":1,\"script\":\"not-valid-base64\"}"))
                .andExpect(status().isGone());
        http.perform(post("/api/v1/database")).andExpect(status().isGone());
    }
}
