package cn.gov.enterprise.modules.system.log.service;

public interface DataChangeLogService {
    void saveChangeLog(
            String moduleName, String operationName, Long businessId, Object beforeData, Object afterData);
}
