package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

public record RoleRuntimeExecutionAdmissionSlot(
        Long candidateSnapshotRowId, String snapshotId, Long activeAdmissionRowId,
        String activeAdmissionId, String activeToken, String slotStatus, int version) {
    public RoleRuntimeExecutionAdmissionSlot {
        if (candidateSnapshotRowId == null || version < 0) throw new IllegalArgumentException("invalid slot identity");
        snapshotId = AdmissionPersistenceSupport.text(snapshotId, "snapshotId", 100);
        slotStatus = AdmissionPersistenceSupport.text(slotStatus, "slotStatus", 16);
        AdmissionPersistenceSupport.hash(activeToken, "activeToken");
        if ((activeAdmissionRowId == null) != (activeAdmissionId == null)) {
            throw new IllegalArgumentException("active admission pointer must be complete");
        }
        if (activeAdmissionId != null) AdmissionPersistenceSupport.text(activeAdmissionId, "activeAdmissionId", 100);
    }

    public boolean vacant() { return activeAdmissionRowId == null; }
}
