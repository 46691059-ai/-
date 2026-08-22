package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.Objects;

/** Final DIRECT selection derived from a frozen candidate pool. */
public record AssignmentResolutionResult(CandidatePool candidatePool, Long selectedUserId) {

    public AssignmentResolutionResult {
        Objects.requireNonNull(candidatePool, "candidatePool");
        if (selectedUserId == null || selectedUserId <= 0
                || !candidatePool.candidateUserIds().contains(selectedUserId)) {
            throw new IllegalArgumentException("selected user must belong to candidate pool");
        }
        if (!selectedUserId.equals(candidatePool.singleUserId())) {
            throw new IllegalArgumentException("WF3.6 supports USER + DIRECT only");
        }
    }

    public static AssignmentResolutionResult direct(CandidatePool pool) {
        return new AssignmentResolutionResult(pool, pool.singleUserId());
    }

    /** Compatibility projection for the immutable V2.6.2 snapshot schema. */
    public AssignmentResult toAssignmentResult() {
        return new AssignmentResult(candidatePool.strategyType(), candidatePool.targetType(),
                candidatePool.targetSnapshot(), candidatePool.candidateUserIds(),
                candidatePool.generatedTime(), candidatePool.auditInfo());
    }
}
