package cn.gov.enterprise.common.api;

import java.time.Instant;
import org.slf4j.MDC;

/**
 * REST接口统一响应对象。
 */
public record ApiResponse<T>(
        Integer code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, MDC.get("traceId"), Instant.now());
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"), Instant.now());
    }
}
