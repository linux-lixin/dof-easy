package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.DofAdminController;
import com.aiyi.game.dnfserver.conf.ExceptionHandlerAdvice;
import com.aiyi.core.exception.AccessOAuthException;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import javax.servlet.http.*;
import java.lang.reflect.Field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class DofClientHttpTest {
    private static DofAdminController controller(DofAdminService service) throws Exception {
        DofAdminController controller=new DofAdminController();
        Field field=DofAdminController.class.getDeclaredField("service");field.setAccessible(true);field.set(controller,service);return controller;
    }
    @Test public void imageErrorsRemainHttpErrorsInsteadOfBrokenPngResponses() throws Exception {
        DofAdminService service=mock(DofAdminService.class);
        when(service.presetImage("missing")).thenThrow(new DofFailure(404,"血槽预设不存在"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(service)).setControllerAdvice(new ExceptionHandlerAdvice()).build();
        org.springframework.mock.web.MockHttpServletResponse response=mvc.perform(get("/api/v1/admin/dof/ui/presets/missing/image")).andReturn().getResponse();
        assertEquals(404,response.getStatus());assertTrue(response.getContentType(),response.getContentType().startsWith("application/json"));
    }
    @Test public void unauthenticatedImageRequestReturns401() throws Exception {
        DofAdminService service=mock(DofAdminService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller(service)).setControllerAdvice(new ExceptionHandlerAdvice())
            .addInterceptors(new HandlerInterceptorAdapter(){@Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){throw new AccessOAuthException();}}).build();
        assertEquals(401,mvc.perform(get("/api/v1/admin/dof/ui/presets/native-100/image")).andReturn().getResponse().getStatus());
        verify(service,never()).presetImage(anyString());
    }
}
