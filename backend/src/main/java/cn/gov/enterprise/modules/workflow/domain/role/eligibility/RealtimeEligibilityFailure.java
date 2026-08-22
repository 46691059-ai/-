package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.Objects;

public record RealtimeEligibilityFailure(RealtimeEligibilityFailureCode code, String reason) {
    public RealtimeEligibilityFailure {
        Objects.requireNonNull(code, "code");
        reason = requireText(reason, "reason", 300);
    }

    public RealtimeEligibilityStatus status() {
        return code.category() == RealtimeEligibilityFailureCode.Category.BUSINESS_REJECT
                ? RealtimeEligibilityStatus.INELIGIBLE : RealtimeEligibilityStatus.INDETERMINATE;
    }

    static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " must contain 1.." + max + " characters");
        }
        return value;
    }
}
