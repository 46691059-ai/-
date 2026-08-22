package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.PersistentRoleRuntimeExecutionAdmission;
import java.util.Optional;

public interface RoleRuntimeExecutionAdmissionRepository {
    void insert(PersistentRoleRuntimeExecutionAdmission admission);
    Optional<PersistentRoleRuntimeExecutionAdmission> findByRequestId(String requestId);
    Optional<PersistentRoleRuntimeExecutionAdmission> findByCandidateAndIdempotencyKey(
            Long candidateSnapshotRowId, String idempotencyKey);
    Optional<PersistentRoleRuntimeExecutionAdmission> findActiveByBindingHash(String bindingHash);
}
