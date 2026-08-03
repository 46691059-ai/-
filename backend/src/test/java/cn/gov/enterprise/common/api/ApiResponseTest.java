package cn.gov.enterprise.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsTraceIdAndTimestampWithIntegerCode() {
        MDC.put("traceId", "trace-test-001");

        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.traceId()).isEqualTo("trace-test-001");
        assertThat(response.timestamp()).isNotNull();
    }
}
