package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

/** Facts re-read under the future short transaction; this class performs no reads or writes. */
public record RealtimeEligibilityVerificationFacts(
        String candidatePoolHash,
        String runtimeBindingHash,
        long taskVersion,
        boolean activeClaimExists,
        Instant now) {
    public RealtimeEligibilityVerificationFacts {
        candidatePoolHash = RealtimeEligibilityQuery.hash(candidatePoolHash, "candidatePoolHash");
        runtimeBindingHash = RealtimeEligibilityQuery.hash(runtimeBindingHash, "runtimeBindingHash");
        if (taskVersion < 0) throw new IllegalArgumentException("taskVersion must not be negative");
        Objects.requireNonNull(now, "now");
    }
}
