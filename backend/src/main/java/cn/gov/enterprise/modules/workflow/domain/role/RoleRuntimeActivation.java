package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.ApproverRole;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.Decision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Activation governance aggregate. WF5.8 deliberately exposes no ENABLE transition. */
public final class RoleRuntimeActivation {
    private static final EnumSet<ApproverRole> REQUIRED_APPROVERS = EnumSet.of(
            ApproverRole.BUSINESS_OWNER,
            ApproverRole.SECURITY_AUDIT,
            ApproverRole.RELEASE_APPROVER);

    private final RoleRuntimeActivationRequest request;
    private final RoleRuntimeActivationStatus status;
    private final String activationHash;
    private final String reason;
    private final List<RoleRuntimeActivationDecision> decisions;

    private RoleRuntimeActivation(
            RoleRuntimeActivationRequest request,
            RoleRuntimeActivationStatus status,
            String activationHash,
            String reason,
            List<RoleRuntimeActivationDecision> decisions) {
        this.request = Objects.requireNonNull(request, "request");
        this.status = Objects.requireNonNull(status, "status");
        this.activationHash = RoleCandidateResult.hash(activationHash, "activationHash");
        this.reason = RoleDirectoryQuery.required(reason, "reason", 300);
        this.decisions = List.copyOf(decisions);
        if (status == RoleRuntimeActivationStatus.ENABLED) {
            throw new IllegalStateException("ROLE Runtime enablement is outside WF5.8");
        }
    }

    public static RoleRuntimeActivation draft(RoleRuntimeActivationRequest request) {
        Objects.requireNonNull(request, "request");
        return new RoleRuntimeActivation(request, RoleRuntimeActivationStatus.DRAFT,
                request.canonicalHash(), "ACTIVATION_DRAFT", List.of());
    }

    public RoleRuntimeActivation evaluate(
            RoleRuntimeActivationGate gate, RoleRuntimeActivationEvidence evidence) {
        requireStatus(RoleRuntimeActivationStatus.DRAFT);
        RoleRuntimeActivationGateResult result = gate.evaluate(request, evidence);
        return new RoleRuntimeActivation(request, result.status(), result.activationHash(),
                result.reason(), List.of());
    }

    public RoleRuntimeActivation decide(RoleRuntimeActivationDecision decision) {
        Objects.requireNonNull(decision, "decision");
        if (decision.approverRole() == ApproverRole.WORKFLOW_ADMIN) {
            throw new IllegalStateException("Workflow administrator has no Activation approval right");
        }
        if (decision.decision() == Decision.REVOKE) return revoke(decision);
        if (status != RoleRuntimeActivationStatus.ELIGIBLE) {
            throw new IllegalStateException("only ELIGIBLE Activation can receive a decision");
        }
        if (decisions.stream().anyMatch(item -> item.approverRole() == decision.approverRole())) {
            throw new IllegalStateException("duplicate Activation decision for approver role");
        }
        List<RoleRuntimeActivationDecision> updated = new ArrayList<>(decisions);
        updated.add(decision);
        if (decision.decision() == Decision.REJECT) {
            return new RoleRuntimeActivation(request, RoleRuntimeActivationStatus.REJECTED,
                    activationHash, decision.reason(), updated);
        }
        EnumSet<ApproverRole> approved = EnumSet.noneOf(ApproverRole.class);
        updated.stream().filter(item -> item.decision() == Decision.APPROVE)
                .map(RoleRuntimeActivationDecision::approverRole).forEach(approved::add);
        RoleRuntimeActivationStatus next = approved.containsAll(REQUIRED_APPROVERS)
                ? RoleRuntimeActivationStatus.APPROVED
                : RoleRuntimeActivationStatus.ELIGIBLE;
        return new RoleRuntimeActivation(request, next, activationHash,
                next == RoleRuntimeActivationStatus.APPROVED
                        ? "ACTIVATION_APPROVED_NOT_ENABLED" : "ACTIVATION_APPROVAL_PENDING",
                updated);
    }

    public RoleRuntimeActivationSnapshot snapshot() {
        requireStatus(RoleRuntimeActivationStatus.APPROVED);
        Instant approvedAt = decisions.stream().map(RoleRuntimeActivationDecision::timestamp)
                .max(Instant::compareTo).orElseThrow();
        String evidenceHash = RoleRuntimeActivationCanonical.snapshotHash(
                request, activationHash, decisions);
        return new RoleRuntimeActivationSnapshot(request, activationHash,
                decisions, approvedAt, evidenceHash);
    }

    private RoleRuntimeActivation revoke(RoleRuntimeActivationDecision decision) {
        requireStatus(RoleRuntimeActivationStatus.APPROVED);
        if (decision.approverRole() != ApproverRole.RELEASE_APPROVER) {
            throw new IllegalStateException("only Release Approver may revoke Activation");
        }
        List<RoleRuntimeActivationDecision> updated = new ArrayList<>(decisions);
        updated.add(decision);
        return new RoleRuntimeActivation(request, RoleRuntimeActivationStatus.REVOKED,
                activationHash, decision.reason(), updated);
    }

    private void requireStatus(RoleRuntimeActivationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Activation status must be " + expected);
        }
    }

    public RoleRuntimeActivationRequest request() { return request; }
    public RoleRuntimeActivationStatus status() { return status; }
    public String activationHash() { return activationHash; }
    public String reason() { return reason; }
    public List<RoleRuntimeActivationDecision> decisions() { return decisions; }
}
