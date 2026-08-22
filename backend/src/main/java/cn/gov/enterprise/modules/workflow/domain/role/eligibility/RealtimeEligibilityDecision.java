package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.Objects;

public record RealtimeEligibilityDecision(
        RealtimeEligibilityStatus status,
        boolean mayEnterClaimVerify,
        RealtimeEligibilityFailure failure) {
    public RealtimeEligibilityDecision {
        Objects.requireNonNull(status, "status");
        if (mayEnterClaimVerify != (status == RealtimeEligibilityStatus.ELIGIBLE)) {
            throw new IllegalArgumentException("only ELIGIBLE may enter Claim Verify");
        }
        if (status == RealtimeEligibilityStatus.ELIGIBLE && failure != null) {
            throw new IllegalArgumentException("ELIGIBLE cannot contain a failure");
        }
        if (status != RealtimeEligibilityStatus.ELIGIBLE && failure == null) {
            throw new IllegalArgumentException("failed decision requires failure");
        }
    }
}
