package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.task.MemberTask;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DofSchedulerIsolationTest {
    @Test public void qingkongAdminDoesNotCreateLegacyCharacterLimitScheduler() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AccountVODao dao = mock(AccountVODao.class);
            context.getBeanFactory().registerSingleton("accountVODao", dao);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Collections.singletonMap("DOF_ADMIN_ENABLED", "true")));
            context.register(MemberTask.class); context.refresh();
            assertTrue(context.getBeansOfType(MemberTask.class).isEmpty()); verifyZeroInteractions(dao);
        }
    }
    @Test public void existingNonDofRuntimeKeepsItsExplicitBehavior() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            AccountVODao dao = mock(AccountVODao.class);
            context.getBeanFactory().registerSingleton("accountVODao", dao);
            context.register(MemberTask.class); context.refresh();
            assertEquals(1, context.getBeansOfType(MemberTask.class).size()); verifyZeroInteractions(dao);
        }
    }
}
