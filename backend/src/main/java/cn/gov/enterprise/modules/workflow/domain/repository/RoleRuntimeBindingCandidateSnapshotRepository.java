package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingCandidateSnapshot;
import java.util.Optional;

public interface RoleRuntimeBindingCandidateSnapshotRepository {
    void insert(RoleRuntimeBindingCandidateSnapshot snapshot);
    Optional<RoleRuntimeBindingCandidateSnapshot> findBySnapshotId(String snapshotId);
    Optional<RoleRuntimeBindingCandidateSnapshot> findByPromotionId(String promotionId);
    Optional<RoleRuntimeBindingCandidateSnapshot> lockBySnapshotId(String snapshotId);
}
