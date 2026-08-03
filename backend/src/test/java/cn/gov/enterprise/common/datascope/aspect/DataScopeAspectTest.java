package cn.gov.enterprise.common.datascope.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataPermissionContextHolder;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import java.lang.reflect.Method;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataScopeAspectTest {
    @Mock DataPermissionService permissionService;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;

    @AfterEach
    void cleanContext() {
        DataPermissionContextHolder.clear();
    }

    @Test
    void establishesContextDuringInvocationAndAlwaysCleansIt() throws Throwable {
        DataScopeAspect aspect = new DataScopeAspect(permissionService);
        ScopedTarget target = new ScopedTarget();
        Method method = ScopedTarget.class.getMethod("query");
        DataPermissionContext context = context();
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(permissionService.current()).thenReturn(context);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            var active = DataPermissionContextHolder.current().orElseThrow();
            assertThat(active.context()).isSameAs(context);
            assertThat(active.rule().orgField()).isEqualTo("biz.org_id");
            assertThat(active.rule().userField()).isEqualTo("biz.owner_user_id");
            return "ok";
        });

        assertThat(aspect.apply(joinPoint)).isEqualTo("ok");
        assertThat(DataPermissionContextHolder.current()).isEmpty();
    }

    @Test
    void clearsContextWhenBusinessInvocationThrows() throws Throwable {
        DataScopeAspect aspect = new DataScopeAspect(permissionService);
        ScopedTarget target = new ScopedTarget();
        Method method = ScopedTarget.class.getMethod("query");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
        when(permissionService.current()).thenReturn(context());
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.apply(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(DataPermissionContextHolder.current()).isEmpty();
    }

    private DataPermissionContext context() {
        return new DataPermissionContext(90003L, "digital_manager", 103L,
                Set.of(5L), DataScopeType.ORG_AND_CHILDREN,
                Set.of(103L, 10301L, 10302L, 10303L), false);
    }

    static class ScopedTarget {
        @DataScope(
                orgField = "biz.org_id",
                userField = "biz.owner_user_id",
                selfValueType = SelfValueType.USER_ID)
        public String query() {
            return "ok";
        }
    }
}
