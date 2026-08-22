package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleDirectoryProviderAuditPort;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only double. Production wiring has no in-memory or no-op fallback. */
final class InMemoryApprovalRoleDirectoryProviderAuditSink implements ApprovalRoleDirectoryProviderAuditPort {
    private final List<ApprovalRoleDirectoryProviderAuditEvidence> events=new CopyOnWriteArrayList<>();
    public void append(ApprovalRoleDirectoryProviderAuditEvidence evidence){events.add(evidence);}
    public List<ApprovalRoleDirectoryProviderAuditEvidence> findRecent(int limit){return events.stream().limit(limit).toList();}
}
