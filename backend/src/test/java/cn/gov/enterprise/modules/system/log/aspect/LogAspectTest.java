package cn.gov.enterprise.modules.system.log.aspect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.support.ExceptionAuditRecorder;
import cn.gov.enterprise.modules.system.log.support.SensitiveDataSanitizer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class LogAspectTest {
    @Mock AuditLogService auditLogService;
    @Mock SensitiveDataSanitizer sanitizer;
    @Mock ExceptionAuditRecorder exceptionAuditRecorder;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;
    private LogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new LogAspect(auditLogService, sanitizer, exceptionAuditRecorder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/system/user");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(DummyController.class);
        when(signature.getMethod()).thenReturn(DummyController.class.getDeclaredMethod("create"));
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(sanitizer.requestParameters(any(String[].class), any(Object[].class))).thenReturn("{}");
    }

    @AfterEach
    void tearDown() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void recordsSuccessfulControllerOperation() throws Throwable {
        ApiResponse<Void> response = ApiResponse.success(null);
        when(joinPoint.proceed()).thenReturn(response);
        when(sanitizer.responseSummary(response)).thenReturn("code=200");

        aspect.auditController(joinPoint);

        verify(auditLogService).save(argThat(command ->
                "SYSTEM_USER".equals(command.moduleName())
                        && "SUCCESS".equals(command.status())
                        && !command.requestParams().contains("password")));
    }

    @Test
    void delegatesExceptionAuditAndRethrows() throws Throwable {
        IllegalStateException failure = new IllegalStateException("failure");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.auditController(joinPoint)).isSameAs(failure);
        verify(exceptionAuditRecorder).record(org.mockito.ArgumentMatchers.same(failure),
                org.mockito.ArgumentMatchers.anyLong());
    }

    private static final class DummyController {
        @SuppressWarnings("unused") void create() {}
    }
}
