package cn.gov.enterprise.modules.organization.approvalrole.domain.repository;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignment;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApprovalRoleAssignmentRepository {
    void insert(ApprovalRoleAssignment assignment);
    Optional<ApprovalRoleAssignment> findByIdForUpdate(long id);
    List<ApprovalRoleAssignment> findEffective(String enterpriseId, long organizationId,
            ApprovalRoleCode roleCode, Instant effectiveAt);
    List<ApprovalRoleAssignment> lockOverlapping(String enterpriseId, long organizationId,
            long roleId, long userId, String sourceSystem, String sourceReference,
            Instant effectiveFrom, Instant effectiveTo);
    List<ApprovalRoleAssignment> history(String enterpriseId, long organizationId, ApprovalRoleCode roleCode);
    boolean update(ApprovalRoleAssignment assignment, int expectedVersion);
}
