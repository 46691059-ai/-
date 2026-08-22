package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

/** Stable owner references, not personal names. */
public record RoleDirectoryOwnershipDescriptor(
        String businessOwnerRef, String dataOwnerRef, String organizationOwnerRef,
        String technicalOwnerRef, String securityAuditOwnerRef) {
    public boolean complete() {
        return present(businessOwnerRef) && present(dataOwnerRef) && present(organizationOwnerRef)
                && present(technicalOwnerRef) && present(securityAuditOwnerRef);
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
}
