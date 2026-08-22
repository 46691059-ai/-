package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Auditable technical qualification only; this type has no runtime activation capability. */
@ConfigurationProperties(prefix="app.workflow-role-directory-technical-gate")
public record RoleDirectoryTechnicalGateProperties(Status status, boolean runtimeActive) {
    public enum Status { PREPARED, EXECUTION_ELIGIBLE }
    public RoleDirectoryTechnicalGateProperties {
        if(status==null)status=Status.PREPARED;
        if(runtimeActive)throw new IllegalArgumentException("technical gate must not activate ROLE runtime");
    }
}
