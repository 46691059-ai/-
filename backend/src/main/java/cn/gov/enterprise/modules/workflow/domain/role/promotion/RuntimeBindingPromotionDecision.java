package cn.gov.enterprise.modules.workflow.domain.role.promotion;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable promotion outcome and its append-only audit trail. */
public record RuntimeBindingPromotionDecision(
        RuntimeBindingPromotionRequest request,
        RuntimeBindingPromotionStatus status,
        String reason,
        RuntimeBindingCandidate candidate,
        RuntimeBindingPromotionAudit audit) {

    public RuntimeBindingPromotionDecision {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(audit, "audit");
        if (audit.currentStatus() != status) throw new IllegalArgumentException("audit status mismatch");
        if ((status == RuntimeBindingPromotionStatus.PROMOTED) != (candidate != null)) {
            throw new IllegalArgumentException("candidate exists only for PROMOTED decision");
        }
    }

    public static RuntimeBindingPromotionDecision created(
            RuntimeBindingPromotionRequest request, Instant at) {
        return new RuntimeBindingPromotionDecision(request, RuntimeBindingPromotionStatus.CREATED,
                "CREATED", null, RuntimeBindingPromotionAudit.created(request.promotionId(), at));
    }

    public RuntimeBindingPromotionDecision advance(
            RuntimeBindingPromotionStatus target, String nextReason, Instant at) {
        if (target == RuntimeBindingPromotionStatus.PROMOTED) {
            throw new IllegalArgumentException("PROMOTED requires a candidate");
        }
        RuntimeBindingPromotionAudit nextAudit = audit.append(target, nextReason, at);
        return new RuntimeBindingPromotionDecision(request, target, nextReason, null, nextAudit);
    }

    public RuntimeBindingPromotionDecision promote(RuntimeBindingCandidate nextCandidate, Instant at) {
        RuntimeBindingPromotionAudit nextAudit = audit.append(
                RuntimeBindingPromotionStatus.PROMOTED, "PROMOTED_AS_NON_EXECUTABLE_CANDIDATE", at);
        return new RuntimeBindingPromotionDecision(request, RuntimeBindingPromotionStatus.PROMOTED,
                "PROMOTED_AS_NON_EXECUTABLE_CANDIDATE", Objects.requireNonNull(nextCandidate), nextAudit);
    }

    public Optional<RuntimeBindingCandidate> candidateOptional() {
        return Optional.ofNullable(candidate);
    }
}
