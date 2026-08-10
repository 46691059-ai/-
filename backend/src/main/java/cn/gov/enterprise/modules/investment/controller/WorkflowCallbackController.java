package cn.gov.enterprise.modules.investment.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowAdapter;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowCallbackSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/investment/workflow/v1")
public class WorkflowCallbackController {
    private final WorkflowAdapter adapter;
    private final WorkflowCallbackSecurityService security;
    private final ObjectMapper objectMapper;

    public WorkflowCallbackController(
            WorkflowAdapter adapter,
            WorkflowCallbackSecurityService security,
            ObjectMapper objectMapper) {
        this.adapter = adapter;
        this.security = security;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/events")
    public ApiResponse<?> event(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Key-Id", required = false) String keyId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Contract-Version", required = false) String contractVersion,
            @RequestHeader(value = "X-Content-SHA256", required = false) String contentSha256,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody byte[] body,
            HttpServletRequest request) throws IOException {
        WorkflowCallbackSecurityService.CallbackHeaders headers =
                new WorkflowCallbackSecurityService.CallbackHeaders(
                        authorization, keyId, timestamp, nonce, requestId, traceId,
                        contractVersion, contentSha256, signature);
        String payloadHash = security.verify(body, headers, request.getMethod(), request.getRequestURI());
        WorkflowEvent event = objectMapper.readValue(body, WorkflowEvent.class);
        var result = adapter.receiveApprovalResult(
                event, new String(body, StandardCharsets.UTF_8), payloadHash);
        if (result == cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowInboxService.Result.REJECTED) {
            throw new BusinessException("B0690", "Workflow事件摘要冲突");
        }
        return ApiResponse.success(result);
    }
}
