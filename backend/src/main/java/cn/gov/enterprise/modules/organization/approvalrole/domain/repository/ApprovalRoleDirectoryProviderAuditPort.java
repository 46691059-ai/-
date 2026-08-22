package cn.gov.enterprise.modules.organization.approvalrole.domain.repository;

import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import java.util.List;

public interface ApprovalRoleDirectoryProviderAuditPort {
    void append(ApprovalRoleDirectoryProviderAuditEvidence evidence);
    List<ApprovalRoleDirectoryProviderAuditEvidence> findRecent(int limit);
}
