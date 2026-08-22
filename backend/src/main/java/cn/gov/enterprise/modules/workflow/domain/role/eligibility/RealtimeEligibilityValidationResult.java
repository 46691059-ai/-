package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

public record RealtimeEligibilityValidationResult(
        String validatorCode,
        int order,
        Status status,
        String reason,
        String evidenceHash,
        Instant checkedAt) {
    public enum Status { PASS, FAIL, INDETERMINATE }

    public RealtimeEligibilityValidationResult {
        validatorCode = RealtimeEligibilityQuery.upperCode(validatorCode, "validatorCode");
        if (order < 1 || order > 27) throw new IllegalArgumentException("order must be 1..27");
        Objects.requireNonNull(status, "status");
        reason = RealtimeEligibilityQuery.text(reason, "reason", 300);
        evidenceHash = RealtimeEligibilityQuery.hash(evidenceHash, "evidenceHash");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
