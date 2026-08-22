package cn.gov.enterprise.modules.workflow.infrastructure.directory;

/** Effective limit is governed by both platform and business owners; results are never truncated. */
public record DirectoryCandidateLimitPolicy(int platformMax, int businessMax) {
    public DirectoryCandidateLimitPolicy {
        if (platformMax <= 0 || businessMax <= 0) throw new IllegalArgumentException("candidate limits must be positive");
    }

    public int effectiveLimit() { return Math.min(platformMax, businessMax); }

    public void verify(int candidateCount) {
        if (candidateCount > effectiveLimit()) {
            throw new DirectoryFailure(DirectoryFailure.Code.CANDIDATE_LIMIT_EXCEEDED,
                    "candidate count exceeds the governed effective limit");
        }
    }
}
