package cn.gov.enterprise.modules.organization.approvalrole.domain.repository;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleRevision;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleRevisionHead;
import java.util.List;
import java.util.Optional;

public interface ApprovalRoleRevisionRepository {
    void insertHead(ApprovalRoleRevisionHead head, String actor);
    Optional<ApprovalRoleRevisionHead> lockHead(String enterpriseId, long organizationId, ApprovalRoleCode roleCode);
    Optional<ApprovalRoleRevisionHead> current(String enterpriseId, long organizationId, ApprovalRoleCode roleCode);
    boolean advance(ApprovalRoleRevisionHead current, long nextRevision, String nextHash, String actor);
    void append(ApprovalRoleRevision revision);
    List<ApprovalRoleRevision> history(String enterpriseId, long organizationId, ApprovalRoleCode roleCode);
    List<ApprovalRoleRevisionHead> listByRole(String enterpriseId, ApprovalRoleCode roleCode);
}
