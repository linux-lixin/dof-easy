package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.ExtensionFailureAdvice;
import com.aiyi.game.dnfserver.controller.NoticeController;
import com.aiyi.game.dnfserver.dao.NoticeDao;
import com.aiyi.game.dnfserver.entity.Notice;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import com.aiyi.game.dnfserver.service.extension.ExtensionQueryService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NoticeRetirementHttpTest {
    private MockMvc http;
    private NoticeDao dao;
    private ExtensionQueryService security;
    @Before public void setup() {
        dao=mock(NoticeDao.class);security=mock(ExtensionQueryService.class);
        NoticeController controller=new NoticeController();
        ReflectionTestUtils.setField(controller,"noticeDao",dao);
        ReflectionTestUtils.setField(controller,"security",security);
        http=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExtensionFailureAdvice()).build();
    }
    @Test public void oldWriteNeverReachesDaoOrEchoesSubmittedContent() throws Exception {
        String marker="private_notice_marker_4901";
        for (int id : new int[]{1,2}) {
            String result=http.perform(put("/api/v1/notice").contentType("application/json")
                    .content("{\"id\":"+id+",\"content\":\""+marker+"\"}"))
                    .andExpect(status().isGone()).andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(result.contains(marker));
        }
        verify(security,times(2)).requireAdmin();verifyZeroInteractions(dao);
    }
    @Test public void emptyBodyAndInventedProtocolFieldsCannotReactivateLegacyWrite() throws Exception {
        http.perform(put("/api/v1/notice")).andExpect(status().isGone());
        http.perform(put("/api/v1/notice").contentType("application/json").content(
                "{\"id\":1,\"content\":\"synthetic\",\"requestId\":\"00000000-0000-4000-8000-000000000049\",\"revision\":\"invented\"}"))
                .andExpect(status().isGone());
        verifyZeroInteractions(dao);
    }
    @Test public void nonAdministratorRemainsForbiddenBeforeRetirementResponse() throws Exception {
        doThrow(new ActivityConfigService.Failure(403,"管理员权限不足")).when(security).requireAdmin();
        http.perform(put("/api/v1/notice").contentType("application/json").content("{\"id\":1,\"content\":\"synthetic\"}"))
                .andExpect(status().isForbidden());
        verifyZeroInteractions(dao);
    }
    @Test public void existingPublicReadWireAndUtf8DecodingRemainCompatible() throws Exception {
        Notice notice=new Notice();notice.setId(1);
        String content="公告\nUTF-8测试🙂";
        notice.setContent(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        when(dao.get(1)).thenReturn(notice);
        http.perform(get("/api/v1/notice")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.content").value(content));
        verifyZeroInteractions(security);verify(dao).get(1);verifyNoMoreInteractions(dao);
    }
}
