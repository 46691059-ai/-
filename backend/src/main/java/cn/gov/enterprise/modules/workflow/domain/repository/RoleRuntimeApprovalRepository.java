package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeApproval;
import java.util.Optional;

/** Insert/query-only port for ROLE Runtime approval evidence. */
public interface RoleRuntimeApprovalRepository {
    void insert(RoleRuntimeApproval approval);
    Optional<RoleRuntimeApproval> findById(Long id);
    Optional<RoleRuntimeApproval> findByHashes(String proposalHash, String eligibilityHash);
}
