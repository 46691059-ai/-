package cn.gov.enterprise.modules.organization.approvalrole.domain.repository;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRole;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import java.util.Optional;

public interface ApprovalRoleRepository {
    void insert(ApprovalRole role);
    Optional<ApprovalRole> findById(long id);
    Optional<ApprovalRole> findByKey(String enterpriseId, ApprovalRoleCode roleCode);
    Optional<ApprovalRole> lockByKey(String enterpriseId, ApprovalRoleCode roleCode);
    boolean updateStatus(ApprovalRole role, int expectedVersion);
}
