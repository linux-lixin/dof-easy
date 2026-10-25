package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.controller.DofAdminController;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.lang.reflect.Field;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** HTTP method/path/body binding and failure status; database semantics have separate contracts. */
public class DofSettingsHttpTest {
    private static final String ROOT="/api/v1/admin/dof/settings/registration";
    private static final String ID="00000000-0000-4000-8000-000000000001";
    private static final String BODY="{\"revision\":\""+String.join("",Collections.nCopies(64,"a"))+"\",\"requestId\":\""+ID+"\",\"values\":{\"ipLimit\":-1,\"activationCode\":true}}";
    private MockMvc mvc(DofSettingsService service)throws Exception {
        DofAdminController controller=new DofAdminController();
        Field f=DofAdminController.class.getDeclaredField("settingsService");f.setAccessible(true);f.set(controller,service);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
    @Test public void previewAndSavePreserveTypedValuesAndDoNotConfuseTheOperations()throws Exception {
        DofSettingsService service=mock(DofSettingsService.class);MockMvc mvc=mvc(service);
        when(service.preview(eq("registration"),any())).thenReturn(Collections.singletonMap("persisted",false));
        assertEquals(200,mvc.perform(post(ROOT+"/preview").contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse().getStatus());
        verify(service,never()).save(anyString(),any(),any());
        assertEquals(200,mvc.perform(put(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse().getStatus());
        ArgumentCaptor<DofSettingsService.Edit> edit=ArgumentCaptor.forClass(DofSettingsService.Edit.class);
        verify(service).save(eq("registration"),edit.capture(),isNull());
        assertEquals(ID,edit.getValue().requestId);assertEquals(-1,edit.getValue().values.get("ipLimit").intValue());
        assertTrue(edit.getValue().values.get("activationCode").isBoolean());
    }
    @Test public void restoreUsesPathTargetAndIndependentBodyRequest()throws Exception {
        DofSettingsService service=mock(DofSettingsService.class);String next="00000000-0000-4000-8000-000000000002";
        String body="{\"revision\":\"abc\",\"requestId\":\""+next+"\"}";
        assertEquals(200,mvc(service).perform(post(ROOT+"/history/"+ID+"/restore").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        ArgumentCaptor<DofSettingsService.Edit> edit=ArgumentCaptor.forClass(DofSettingsService.Edit.class);
        verify(service).save(eq("registration"),edit.capture(),eq(ID));assertEquals(next,edit.getValue().requestId);assertNull(edit.getValue().values);
    }
    @Test public void conflictRemains409AndDatabaseFailureRemains503()throws Exception {
        DofSettingsService service=mock(DofSettingsService.class);MockMvc mvc=mvc(service);
        when(service.save(eq("registration"),any(),isNull())).thenThrow(new DofFailure(409,"配置已经改变"));
        assertEquals(409,mvc.perform(put(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse().getStatus());
        when(service.reconcile("registration",ID)).thenThrow(new DofFailure(503,"结果待核对"));
        assertEquals(503,mvc.perform(post(ROOT+"/history/"+ID+"/reconcile")).andReturn().getResponse().getStatus());
    }
    @Test public void malformedJsonAndWrongMethodsCannotInvokeSave()throws Exception {
        DofSettingsService service=mock(DofSettingsService.class);MockMvc mvc=mvc(service);
        assertEquals(400,mvc.perform(put(ROOT).contentType(MediaType.APPLICATION_JSON).content("{")).andReturn().getResponse().getStatus());
        assertEquals(405,mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse().getStatus());
        verify(service,never()).save(anyString(),any(),any());
    }
}
