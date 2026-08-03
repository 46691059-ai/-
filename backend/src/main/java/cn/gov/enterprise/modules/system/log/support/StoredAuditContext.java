package cn.gov.enterprise.modules.system.log.support;

public record StoredAuditContext(String username, String requestParams, String responseResult) {
}
