package cn.gov.enterprise.modules.workflow.domain.role;

import java.time.Instant;
import java.util.Objects;

/** One immutable RACI decision. Workflow administrators have no approval authority. */
public record RoleRuntimeActivationDecision(
        Decision decision,
        ApproverRole approverRole,
        String approver,
        String reason,
        Instant timestamp,
        String evidenceHash) {

    public enum Decision { APPROVE, REJECT, REVOKE }

    public enum ApproverRole {
        WORKFLOW_ADMIN,
        BUSINESS_OWNER,
        SECURITY_AUDIT,
        RELEASE_APPROVER
    }

    public RoleRuntimeActivationDecision {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(approverRole, "approverRole");
        approver = RoleDirectoryQuery.required(approver, "approver", 100);
        reason = RoleDirectoryQuery.required(reason, "reason", 300);
        Objects.requireNonNull(timestamp, "timestamp");
        evidenceHash = RoleCandidateResult.hash(evidenceHash, "evidenceHash");
    }
}
