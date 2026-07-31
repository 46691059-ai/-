package cn.gov.enterprise.common.api;

import java.time.Instant;
import org.slf4j.MDC;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, MDC.get("traceId"), Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"), Instant.now());
    }
}
