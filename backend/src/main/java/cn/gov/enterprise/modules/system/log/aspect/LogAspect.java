package cn.gov.enterprise.modules.system.log.aspect;

import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.support.AuditRequestContext;
import cn.gov.enterprise.modules.system.log.support.AuditRequestSupport;
import cn.gov.enterprise.modules.system.log.support.ExceptionAuditRecorder;
import cn.gov.enterprise.modules.system.log.support.SensitiveDataSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class LogAspect {
    private final AuditLogService auditLogService;
    private final SensitiveDataSanitizer sanitizer;
    private final ExceptionAuditRecorder exceptionAuditRecorder;

    public LogAspect(
            AuditLogService auditLogService,
            SensitiveDataSanitizer sanitizer,
            ExceptionAuditRecorder exceptionAuditRecorder) {
        this.auditLogService = auditLogService;
        this.sanitizer = sanitizer;
        this.exceptionAuditRecorder = exceptionAuditRecorder;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object auditController(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        if (request.getRequestURI().endsWith("/auth/login")) return joinPoint.proceed();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String module = inferModule(request);
        String operation = signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
        String params = sanitizer.requestParameters(signature.getParameterNames(), joinPoint.getArgs());
        request.setAttribute(AuditRequestContext.MODULE, module);
        request.setAttribute(AuditRequestContext.OPERATION, operation);
        request.setAttribute(AuditRequestContext.PARAMS, params);
        long started = System.nanoTime();
        try {
            Object response = joinPoint.proceed();
            long duration = elapsedMillis(started);
            auditLogService.save(new AuditLogCommand(AuditRequestSupport.userId(), AuditRequestSupport.username(),
                    "OPERATION", module, operation, request.getRequestURI(), request.getMethod(), params,
                    sanitizer.responseSummary(response), AuditRequestSupport.clientIp(request), "SUCCESS",
                    null, duration, AuditRequestSupport.traceId()));
            return response;
        } catch (Throwable exception) {
            exceptionAuditRecorder.record(exception, elapsedMillis(started));
            throw exception;
        }
    }

    private String inferModule(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if ("system".equalsIgnoreCase(segments[index])) {
                return ("SYSTEM_" + segments[index + 1]).toUpperCase(Locale.ROOT);
            }
        }
        for (String segment : segments) {
            if (!segment.isBlank() && !"api".equalsIgnoreCase(segment)) return segment.toUpperCase(Locale.ROOT);
        }
        return "SYSTEM";
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
