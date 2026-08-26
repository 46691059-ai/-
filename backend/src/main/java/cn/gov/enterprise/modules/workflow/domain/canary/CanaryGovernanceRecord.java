package cn.gov.enterprise.modules.workflow.domain.canary;

import java.time.Instant;
import java.util.Objects;

/** Append-only Canary authorization revision. Approval never enables execution. */
public record CanaryGovernanceRecord(long id, Long previousRecordId, CanaryScope scope,
        CanaryGovernanceState state, long revision, CanaryApprovalEvidence evidence,
        String approvalActor, Instant approvedAt, String enablementActor, Instant enabledAt,
        Instant suspendedAt, Instant revokedAt, String reason, Instant effectiveFrom,
        Instant effectiveTo) {
    public CanaryGovernanceRecord {
        if (id <= 0 || revision <= 0) throw new IllegalArgumentException("id/revision must be positive");
        Objects.requireNonNull(scope); Objects.requireNonNull(state); Objects.requireNonNull(evidence);
        Objects.requireNonNull(effectiveFrom);
        if (reason == null || reason.isBlank() || reason.length() > 500
                || (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("invalid Canary governance reason/window");
        }
    }

    public static CanaryGovernanceRecord proposed(long id, CanaryScope scope,
            CanaryApprovalEvidence evidence, String reason, Instant at) {
        return new CanaryGovernanceRecord(id, null, scope, CanaryGovernanceState.PROPOSED, 1,
                evidence, null, null, null, null, null, null, reason, at, null);
    }

    public CanaryGovernanceRecord transition(long newId, CanaryGovernanceState target,
            String actor, String transitionReason, Instant at) {
        Objects.requireNonNull(target); requireActor(actor); Objects.requireNonNull(at);
        boolean allowed = switch (state) {
            case PROPOSED -> target == CanaryGovernanceState.APPROVED_NOT_ENABLED || target == CanaryGovernanceState.REVOKED;
            case APPROVED_NOT_ENABLED -> target == CanaryGovernanceState.ENABLED || target == CanaryGovernanceState.REVOKED;
            case ENABLED -> target == CanaryGovernanceState.SUSPENDED || target == CanaryGovernanceState.REVOKED;
            case SUSPENDED -> target == CanaryGovernanceState.ENABLED || target == CanaryGovernanceState.REVOKED;
            case REVOKED -> false;
        };
        if (!allowed) throw new IllegalStateException("invalid Canary governance transition");
        String approvedBy = approvalActor; Instant approvalTime = approvedAt;
        String enabledBy = enablementActor; Instant enablementTime = enabledAt;
        Instant suspensionTime = suspendedAt; Instant revocationTime = revokedAt;
        if (target == CanaryGovernanceState.APPROVED_NOT_ENABLED) { approvedBy=actor; approvalTime=at; }
        if (target == CanaryGovernanceState.ENABLED) { enabledBy=actor; enablementTime=at; suspensionTime=null; }
        if (target == CanaryGovernanceState.SUSPENDED) suspensionTime=at;
        if (target == CanaryGovernanceState.REVOKED) revocationTime=at;
        return new CanaryGovernanceRecord(newId, id, scope, target, revision+1, evidence,
                approvedBy, approvalTime, enabledBy, enablementTime, suspensionTime,
                revocationTime, transitionReason, at, effectiveTo);
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
