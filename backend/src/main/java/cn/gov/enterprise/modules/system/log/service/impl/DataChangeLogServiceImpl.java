package cn.gov.enterprise.modules.system.log.service.impl;

import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.service.AuditLogService;
import cn.gov.enterprise.modules.system.log.service.DataChangeLogService;
import cn.gov.enterprise.modules.system.log.support.AuditRequestSupport;
import cn.gov.enterprise.modules.system.log.support.SensitiveDataSanitizer;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DataChangeLogServiceImpl implements DataChangeLogService {
    private final AuditLogService auditLogService;
    private final SensitiveDataSanitizer sanitizer;

    public DataChangeLogServiceImpl(AuditLogService auditLogService, SensitiveDataSanitizer sanitizer) {
        this.auditLogService = auditLogService; this.sanitizer = sanitizer;
    }

    @Override
    public void saveChangeLog(
            String moduleName, String operationName, Long businessId, Object beforeData, Object afterData) {
        String change = sanitizer.sanitize(Map.of(
                "businessId", businessId == null ? "" : businessId,
                "before", beforeData == null ? Map.of() : beforeData,
                "after", afterData == null ? Map.of() : afterData), 240);
        auditLogService.save(new AuditLogCommand(AuditRequestSupport.userId(), AuditRequestSupport.username(),
                "DATA_CHANGE", moduleName, operationName, null, null, change, "saved", null,
                "SUCCESS", null, 0L, AuditRequestSupport.traceId()));
    }
}
