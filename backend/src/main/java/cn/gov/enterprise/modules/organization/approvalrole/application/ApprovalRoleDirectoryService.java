package cn.gov.enterprise.modules.organization.approvalrole.application;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRole;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleAssignment;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryMember;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryPolicy;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryQuery;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryResult;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryFailure;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryFailureCode;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleRevision;
import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleRevisionHead;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleAssignmentRepository;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleRepository;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleRevisionRepository;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.OrganizationUserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Internal Organization/Governance directory boundary; no HTTP endpoint is exposed. */
@Service
public class ApprovalRoleDirectoryService {
    private final ApprovalRoleRepository roles;
    private final ApprovalRoleAssignmentRepository assignments;
    private final ApprovalRoleRevisionRepository revisions;
    private final OrganizationUserDirectoryPort masterData;
    private final ApprovalRoleDirectoryPolicy policy = new ApprovalRoleDirectoryPolicy();
    private final Clock clock;

    @Autowired
    public ApprovalRoleDirectoryService(ApprovalRoleRepository roles,
            ApprovalRoleAssignmentRepository assignments, ApprovalRoleRevisionRepository revisions,
            OrganizationUserDirectoryPort masterData) {
        this(roles, assignments, revisions, masterData, Clock.systemUTC());
    }

    ApprovalRoleDirectoryService(ApprovalRoleRepository roles,
            ApprovalRoleAssignmentRepository assignments, ApprovalRoleRevisionRepository revisions,
            OrganizationUserDirectoryPort masterData, Clock clock) {
        this.roles = roles; this.assignments = assignments; this.revisions = revisions;
        this.masterData = masterData; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ApprovalRoleDirectoryResult resolve(ApprovalRoleDirectoryQuery query) {
        ApprovalRole role = roles.findByKey(query.enterpriseId(), query.roleCode())
                .orElseThrow(() -> failure(ApprovalRoleDirectoryFailureCode.ROLE_NOT_FOUND, "approval role not found"));
        if (role.status() != cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleStatus.ACTIVE) {
            throw failure(ApprovalRoleDirectoryFailureCode.ROLE_INACTIVE, "approval role is inactive");
        }
        if (!masterData.activeOrganization(query.organizationId())) {
            throw failure(ApprovalRoleDirectoryFailureCode.ORGANIZATION_INACTIVE, "organization is not active");
        }
        ApprovalRoleRevisionHead head = revisions.current(query.enterpriseId(), query.organizationId(), query.roleCode())
                .orElseThrow(() -> failure(ApprovalRoleDirectoryFailureCode.REVISION_INCONSISTENT, "revision head missing"));
        if (head.currentRevision() <= 0) throw failure(ApprovalRoleDirectoryFailureCode.REVISION_INCONSISTENT, "revision is not published");
        return resolveAt(query, head.currentRevision(), clock.instant());
    }

    ApprovalRoleDirectoryResult resolveAt(ApprovalRoleDirectoryQuery query, long revision, Instant resolvedAt) {
        List<ApprovalRoleAssignment> effective = policy.effective(
                assignments.findEffective(query.enterpriseId(), query.organizationId(), query.roleCode(), query.effectiveAt()),
                query.effectiveAt());
        policy.rejectSourceConflict(effective);
        Map<Long, List<ApprovalRoleDirectoryMember.Evidence>> grouped = new LinkedHashMap<>();
        effective.stream().sorted(java.util.Comparator.comparingLong(ApprovalRoleAssignment::userId)
                .thenComparingLong(ApprovalRoleAssignment::id)).forEach(a -> grouped
                .computeIfAbsent(a.userId(), ignored -> new ArrayList<>())
                .add(new ApprovalRoleDirectoryMember.Evidence(a.id(), a.effectiveFrom(), a.effectiveTo(), a.source())));
        List<ApprovalRoleDirectoryMember> members = grouped.entrySet().stream()
                .map(e -> new ApprovalRoleDirectoryMember(e.getKey(), e.getValue())).toList();
        return ApprovalRoleDirectoryResult.complete(query.enterpriseId(), query.organizationId(),
                query.roleCode(), query.effectiveAt(), revision, members, resolvedAt);
    }

    @Transactional(readOnly = true)
    public long getCurrentRevision(String enterpriseId, long organizationId, String roleCode) {
        return revisions.current(enterpriseId, organizationId, new ApprovalRoleCode(roleCode))
                .map(ApprovalRoleRevisionHead::currentRevision).orElse(0L);
    }
    @Transactional(readOnly = true) public ApprovalRole getRole(String enterpriseId, String roleCode) {
        return roles.findByKey(enterpriseId, new ApprovalRoleCode(roleCode)).orElseThrow();
    }
    @Transactional(readOnly = true) public List<ApprovalRoleAssignment> getAssignmentHistory(
            String enterpriseId, long organizationId, String roleCode) {
        return assignments.history(enterpriseId, organizationId, new ApprovalRoleCode(roleCode));
    }
    @Transactional(readOnly = true) public List<ApprovalRoleRevision> getRevisionHistory(
            String enterpriseId, long organizationId, String roleCode) {
        return revisions.history(enterpriseId, organizationId, new ApprovalRoleCode(roleCode));
    }

    private ApprovalRoleDirectoryFailure failure(ApprovalRoleDirectoryFailureCode code, String message) {
        return new ApprovalRoleDirectoryFailure(code, message);
    }
}
