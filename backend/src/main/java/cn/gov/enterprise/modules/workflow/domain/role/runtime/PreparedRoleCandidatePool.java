package cn.gov.enterprise.modules.workflow.domain.role.runtime;

import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult;
import java.util.Objects;

/** Verified Directory result prepared outside the database write transaction. */
public record PreparedRoleCandidatePool(
        WorkflowInstance instance, WorkflowNodeExecution execution,
        NodeResolverBinding nodeBinding, WorkflowResolverBinding resolverBinding,
        RoleDirectoryRuntimeContext context, RoleCandidateResult candidates,
        RoleDirectoryCandidateEvidence evidence) {
    public PreparedRoleCandidatePool {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(nodeBinding, "nodeBinding");
        Objects.requireNonNull(resolverBinding, "resolverBinding");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.candidateCount() != candidates.candidateUsers().size()) {
            throw new IllegalArgumentException("candidate evidence count mismatch");
        }
    }
}
