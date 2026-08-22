package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

public record RealtimeEligibilityRevisionFence(
        String candidateRevision,
        String claimRevision,
        String claimDirectoryResultHash,
        String contractHash,
        Instant verifiedAt) {
    public RealtimeEligibilityRevisionFence {
        candidateRevision = RealtimeEligibilityQuery.text(candidateRevision, "candidateRevision", 100);
        claimRevision = RealtimeEligibilityQuery.text(claimRevision, "claimRevision", 100);
        claimDirectoryResultHash = RealtimeEligibilityQuery.hash(claimDirectoryResultHash, "claimDirectoryResultHash");
        contractHash = RealtimeEligibilityQuery.hash(contractHash, "contractHash");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
