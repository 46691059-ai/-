package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Duration;
import java.util.Objects;

public record RealtimeEligibilityPolicy(
        Duration maxEligibilityAge,
        String canonicalVersion,
        int directoryRetryBudget,
        Duration directoryTimeout) {
    public static final String CANONICAL_VERSION = "ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1";

    public RealtimeEligibilityPolicy {
        Objects.requireNonNull(maxEligibilityAge, "maxEligibilityAge");
        Objects.requireNonNull(directoryTimeout, "directoryTimeout");
        if (maxEligibilityAge.isZero() || maxEligibilityAge.isNegative()) {
            throw new IllegalArgumentException("maxEligibilityAge must be positive");
        }
        if (directoryTimeout.isZero() || directoryTimeout.isNegative()) {
            throw new IllegalArgumentException("directoryTimeout must be positive");
        }
        if (directoryRetryBudget < 0) throw new IllegalArgumentException("directoryRetryBudget must not be negative");
        if (!CANONICAL_VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported canonicalVersion");
        }
    }

    public static RealtimeEligibilityPolicy testPolicy(Duration maxAge) {
        return new RealtimeEligibilityPolicy(maxAge, CANONICAL_VERSION, 0, Duration.ofSeconds(1));
    }
}
