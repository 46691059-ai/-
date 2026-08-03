package cn.gov.enterprise.modules.system.log.service;

public interface AuditLogService {
    void save(AuditLogCommand command);
}
