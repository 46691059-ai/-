package cn.gov.enterprise.modules.workflow.infrastructure.directory;

@FunctionalInterface
public interface RoleDirectoryAuditPort {
    void append(DirectoryResolutionAuditEvidence evidence);
    static RoleDirectoryAuditPort noop() { return evidence -> { }; }
}
