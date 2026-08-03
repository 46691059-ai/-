package cn.gov.enterprise.modules.system.log.support;

import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ExceptionAuditRecorder {
    private final AuditLogService auditLogService;
    private final SensitiveDataSanitizer sanitizer;

    public ExceptionAuditRecorder(AuditLogService auditLogService, SensitiveDataSanitizer sanitizer) {
        this.auditLogService = auditLogService; this.sanitizer = sanitizer;
    }

    public void record(Throwable exception, Long durationMs) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        HttpServletRequest request = attributes.getRequest();
        if (request.getRequestURI().endsWith("/auth/login")
                || Boolean.TRUE.equals(request.getAttribute(AuditRequestContext.WRITTEN))) return;
        request.setAttribute(AuditRequestContext.WRITTEN, true);
        String module = stringAttribute(request, AuditRequestContext.MODULE, inferModule(request));
        String operation = stringAttribute(request, AuditRequestContext.OPERATION, request.getMethod());
        String params = stringAttribute(request, AuditRequestContext.PARAMS, null);
        auditLogService.save(new AuditLogCommand(AuditRequestSupport.userId(), AuditRequestSupport.username(),
                "ERROR", module, operation, request.getRequestURI(), request.getMethod(), params,
                "exception=" + exception.getClass().getSimpleName(), AuditRequestSupport.clientIp(request),
                "ERROR", sanitizer.sanitizeMessage(exception.getMessage(), 1000), durationMs,
                AuditRequestSupport.traceId()));
    }

    private String stringAttribute(HttpServletRequest request, String name, String fallback) {
        Object value = request.getAttribute(name);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private String inferModule(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if ("system".equalsIgnoreCase(segments[index])) {
                return ("SYSTEM_" + segments[index + 1]).toUpperCase(java.util.Locale.ROOT);
            }
        }
        return "SYSTEM";
    }
}
