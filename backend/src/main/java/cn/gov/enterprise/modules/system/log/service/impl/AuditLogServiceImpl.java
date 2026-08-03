package cn.gov.enterprise.modules.system.log.service.impl;

import cn.gov.enterprise.modules.system.entity.SysLogEntity;
import cn.gov.enterprise.modules.system.mapper.SysLogMapper;
import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.support.SensitiveDataSanitizer;
import cn.gov.enterprise.modules.system.log.support.StoredAuditContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);
    private final SysLogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final SensitiveDataSanitizer sanitizer;

    public AuditLogServiceImpl(SysLogMapper logMapper, ObjectMapper objectMapper, SensitiveDataSanitizer sanitizer) {
        this.logMapper = logMapper; this.objectMapper = objectMapper; this.sanitizer = sanitizer;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void save(AuditLogCommand command) {
        try {
            SysLogEntity entity = new SysLogEntity();
            entity.setUserId(command.userId());
            entity.setOperation(sanitizer.truncate(String.join("|",
                    safe(command.logType(), "OPERATION"), safe(command.moduleName(), "UNKNOWN"),
                    safe(command.operationName(), "UNKNOWN")), 100));
            entity.setRequestUrl(sanitizer.truncate(command.requestUrl(), 500));
            entity.setRequestMethod(sanitizer.truncate(command.requestMethod(), 20));
            entity.setIp(sanitizer.truncate(command.ip(), 64));
            entity.setResult(sanitizer.truncate(command.status(), 20));
            entity.setErrorMessage(sanitizer.sanitizeMessage(command.errorMessage(), 1000));
            entity.setDurationMs(command.durationMs());
            entity.setTraceId(sanitizer.truncate(command.traceId(), 64));
            StoredAuditContext context = new StoredAuditContext(
                    sanitizer.truncate(command.username(), 64),
                    sanitizer.truncate(command.requestParams(), 240),
                    sanitizer.truncate(command.responseResult(), 150));
            entity.setRemark(sanitizer.truncate(objectMapper.writeValueAsString(context), 500));
            logMapper.insert(entity);
        } catch (Exception exception) {
            // 审计写入失败不得覆盖原业务结果，也不得输出请求参数。
            log.error("Audit log persistence failed: type={}", exception.getClass().getSimpleName());
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().replace('|', '_');
    }
}
