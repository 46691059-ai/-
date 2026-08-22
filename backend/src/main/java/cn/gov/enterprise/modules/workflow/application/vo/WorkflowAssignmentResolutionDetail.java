package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only projection of a resolver result; it never re-runs assignment resolution. */
public record WorkflowAssignmentResolutionDetail(
        Source source, Long taskId, Long nodeExecutionId, String strategyType,
        String resolverVersion, String selectionMode, List<Candidate> candidates,
        Long selectedUserId, LocalDateTime generatedTime, String auditInfo) {

    public enum Source { STRUCTURED_SNAPSHOT, LEGACY_TASK_FIELDS }

    public record Candidate(Long userId, String sourceReference, int rank) { }

    public static WorkflowAssignmentResolutionDetail structured(AssignmentSnapshot snapshot) {
        String resolverVersion = snapshot.auditInfo().contains(ExplicitUserResolver.VERSION)
                ? ExplicitUserResolver.VERSION : "V262_USER_DIRECT_COMPAT";
        List<Candidate> candidates = snapshot.resolvedUserIds().stream()
                .map(userId -> new Candidate(userId, "EXPLICIT_USER:" + userId, 1))
                .toList();
        Long selected = candidates.size() == 1 ? candidates.getFirst().userId() : null;
        return new WorkflowAssignmentResolutionDetail(Source.STRUCTURED_SNAPSHOT,
                snapshot.taskId(), snapshot.nodeExecutionId(), snapshot.strategyType().name(),
                resolverVersion, "DIRECT", candidates, selected, snapshot.resolveTime(),
                snapshot.auditInfo());
    }

    public static WorkflowAssignmentResolutionDetail legacy(WorkflowTask task) {
        List<Candidate> candidates = task.assigneeUserId() == null ? List.of()
                : List.of(new Candidate(task.assigneeUserId(), "LEGACY_TASK_ASSIGNEE", 1));
        return new WorkflowAssignmentResolutionDetail(Source.LEGACY_TASK_FIELDS,
                task.id(), task.nodeExecutionId(), "LEGACY_DIRECT_USER", "LEGACY_UNVERSIONED",
                "DIRECT", candidates, task.assigneeUserId(), null,
                "Legacy projection only; resolver execution was not inferred");
    }
}
