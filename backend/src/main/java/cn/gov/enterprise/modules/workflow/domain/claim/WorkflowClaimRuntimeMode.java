package cn.gov.enterprise.modules.workflow.domain.claim;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;

/** Explicitly separates the stable USER/DIRECT path from governed ROLE Claim. */
public enum WorkflowClaimRuntimeMode {
    USER_DIRECT,
    ROLE_CLAIM;

    public static WorkflowClaimRuntimeMode resolve(WorkflowTask task, CandidatePool pool) {
        if (task.assignmentMode() == WorkflowTask.AssignmentMode.DIRECT) return USER_DIRECT;
        if (pool.strategyType() == AssignmentStrategy.Type.ROLE
                && "ROLE_DIRECTORY_V1".equals(pool.resolverCode())) return ROLE_CLAIM;
        throw new IllegalArgumentException("unsupported Candidate Pool runtime mode");
    }
}
