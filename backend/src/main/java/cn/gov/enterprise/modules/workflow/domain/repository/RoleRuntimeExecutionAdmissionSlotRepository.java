package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionSlot;
import java.util.Optional;

public interface RoleRuntimeExecutionAdmissionSlotRepository {
    void ensure(Long candidateSnapshotRowId, String snapshotId);
    Optional<RoleRuntimeExecutionAdmissionSlot> lockByCandidateSnapshotRowId(Long candidateSnapshotRowId);
    boolean compareAndSetActive(Long candidateSnapshotRowId, int expectedVersion, String expectedToken,
            Long admissionRowId, String admissionId, String operator);
    boolean compareAndSetVacant(Long candidateSnapshotRowId, int expectedVersion, String expectedToken, String operator);
}
