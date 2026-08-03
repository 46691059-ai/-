package cn.gov.enterprise.modules.system.log.vo;

import java.time.LocalDateTime;

public record LogVO(
        Long id,
        Long userId,
        String username,
        String logType,
        String moduleName,
        String operation,
        String requestUrl,
        String requestMethod,
        String requestParams,
        String responseResult,
        String ip,
        String status,
        String errorMessage,
        Long durationMs,
        String traceId,
        LocalDateTime createTime) {
}
