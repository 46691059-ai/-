package cn.gov.enterprise.modules.system.log.service;

public record AuditLogCommand(
        Long userId,
        String username,
        String logType,
        String moduleName,
        String operationName,
        String requestUrl,
        String requestMethod,
        String requestParams,
        String responseResult,
        String ip,
        String status,
        String errorMessage,
        Long durationMs,
        String traceId) {
}
