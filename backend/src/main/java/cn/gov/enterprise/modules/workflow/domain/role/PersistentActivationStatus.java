package cn.gov.enterprise.modules.workflow.domain.role;

/** Persistence lifecycle only. PERSISTED never means ROLE Runtime is enabled. */
public enum PersistentActivationStatus {
    DRAFT, SUBMITTED, APPROVAL_PENDING, APPROVED, PERSISTED,
    REJECTED, REVOKED, BLOCKED
}
