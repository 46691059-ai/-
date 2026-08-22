package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RoleRuntimeExecutionAdmissionDecision {
    private static final Map<RoleRuntimeExecutionAdmissionStatus, Set<RoleRuntimeExecutionAdmissionStatus>>
            TRANSITIONS = Map.of(
                    RoleRuntimeExecutionAdmissionStatus.CREATED,
                    EnumSet.of(RoleRuntimeExecutionAdmissionStatus.VALIDATING,
                            RoleRuntimeExecutionAdmissionStatus.REJECTED,
                            RoleRuntimeExecutionAdmissionStatus.REVOKED),
                    RoleRuntimeExecutionAdmissionStatus.VALIDATING,
                    EnumSet.of(RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,
                            RoleRuntimeExecutionAdmissionStatus.BLOCKED,
                            RoleRuntimeExecutionAdmissionStatus.REJECTED,
                            RoleRuntimeExecutionAdmissionStatus.REVOKED),
                    RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,
                    EnumSet.of(RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,
                            RoleRuntimeExecutionAdmissionStatus.BLOCKED,
                            RoleRuntimeExecutionAdmissionStatus.REJECTED,
                            RoleRuntimeExecutionAdmissionStatus.REVOKED,
                            RoleRuntimeExecutionAdmissionStatus.EXPIRED),
                    RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,
                    EnumSet.of(RoleRuntimeExecutionAdmissionStatus.REVOKED,
                            RoleRuntimeExecutionAdmissionStatus.EXPIRED));

    private final String admissionId;
    private final RoleRuntimeExecutionAdmissionStatus status;
    private final RoleRuntimeExecutionBlockReason reason;
    private final RoleRuntimeExecutionAdmissionEvidence evidence;
    private final ExecutableRuntimeBindingCandidate candidate;
    private final Instant decidedAt;

    private RoleRuntimeExecutionAdmissionDecision(String admissionId,
            RoleRuntimeExecutionAdmissionStatus status,
            RoleRuntimeExecutionBlockReason reason,
            RoleRuntimeExecutionAdmissionEvidence evidence,
            ExecutableRuntimeBindingCandidate candidate,
            Instant decidedAt) {
        this.admissionId = RoleRuntimeExecutionAdmissionRequest.text(
                admissionId, "admissionId", 100);
        this.status = Objects.requireNonNull(status, "status");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.evidence = evidence;
        this.candidate = candidate;
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        if (candidate != null && status != RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION) {
            throw new IllegalArgumentException("only approved admission can carry a candidate");
        }
    }

    public static RoleRuntimeExecutionAdmissionDecision created(String admissionId, Instant at) {
        return new RoleRuntimeExecutionAdmissionDecision(admissionId,
                RoleRuntimeExecutionAdmissionStatus.CREATED,
                RoleRuntimeExecutionBlockReason.NONE, null, null, at);
    }

    public RoleRuntimeExecutionAdmissionDecision transition(
            RoleRuntimeExecutionAdmissionStatus target, Instant at) {
        Set<RoleRuntimeExecutionAdmissionStatus> allowed = TRANSITIONS.getOrDefault(status, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException("illegal admission transition " + status + " -> " + target);
        }
        RoleRuntimeExecutionAdmissionEvidence retainedEvidence =
                target == RoleRuntimeExecutionAdmissionStatus.REVOKED
                        || target == RoleRuntimeExecutionAdmissionStatus.EXPIRED
                        ? evidence : null;
        return new RoleRuntimeExecutionAdmissionDecision(
                admissionId, target, RoleRuntimeExecutionBlockReason.NONE,
                retainedEvidence, null, at);
    }

    RoleRuntimeExecutionAdmissionDecision blocked(RoleRuntimeExecutionBlockReason blockReason,
            RoleRuntimeExecutionAdmissionEvidence admissionEvidence, Instant at) {
        if (!TRANSITIONS.getOrDefault(status, Set.of())
                .contains(RoleRuntimeExecutionAdmissionStatus.BLOCKED)) {
            throw new IllegalStateException("admission cannot be blocked from " + status);
        }
        return new RoleRuntimeExecutionAdmissionDecision(admissionId,
                RoleRuntimeExecutionAdmissionStatus.BLOCKED, blockReason,
                admissionEvidence, null, at);
    }

    RoleRuntimeExecutionAdmissionDecision approved(
            RoleRuntimeExecutionAdmissionEvidence admissionEvidence,
            ExecutableRuntimeBindingCandidate executableCandidate, Instant at) {
        if (status != RoleRuntimeExecutionAdmissionStatus.ELIGIBLE) {
            throw new IllegalStateException("only eligible admission can be approved");
        }
        return new RoleRuntimeExecutionAdmissionDecision(admissionId,
                RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,
                RoleRuntimeExecutionBlockReason.NONE, admissionEvidence,
                executableCandidate, at);
    }

    public String admissionId() { return admissionId; }
    public RoleRuntimeExecutionAdmissionStatus status() { return status; }
    public RoleRuntimeExecutionBlockReason reason() { return reason; }
    public Optional<RoleRuntimeExecutionAdmissionEvidence> evidence() {
        return Optional.ofNullable(evidence);
    }
    public Optional<ExecutableRuntimeBindingCandidate> candidate() {
        return Optional.ofNullable(candidate);
    }
    public Instant decidedAt() { return decidedAt; }
}
