package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import java.time.LocalDateTime;
import java.util.List;

/** Read model that preserves legacy tasks without inventing assignment provenance. */
public record WorkflowTaskAssignmentDetail(
        Source source, Long snapshotId, Long taskId, Long nodeExecutionId,
        String strategyType, String targetType, String targetSnapshot,
        List<Long> resolvedUserIds, LocalDateTime resolveTime,
        String auditInfo, String traceId) {

    public enum Source { STRUCTURED_V262, LEGACY_TASK_FIELDS }

    public static WorkflowTaskAssignmentDetail structured(AssignmentSnapshot snapshot) {
        return new WorkflowTaskAssignmentDetail(Source.STRUCTURED_V262, snapshot.id(),
                snapshot.taskId(), snapshot.nodeExecutionId(), snapshot.strategyType().name(),
                snapshot.targetType().name(), snapshot.targetSnapshot(),
                snapshot.resolvedUserIds(), snapshot.resolveTime(), snapshot.auditInfo(),
                snapshot.traceId());
    }

    public static WorkflowTaskAssignmentDetail legacy(WorkflowTask task) {
        List<Long> users = task.assigneeUserId() == null
                ? List.of() : List.of(task.assigneeUserId());
        return new WorkflowTaskAssignmentDetail(Source.LEGACY_TASK_FIELDS, null, task.id(),
                task.nodeExecutionId(), "LEGACY_DIRECT_USER", "USER",
                task.candidateSnapshot(), users, null,
                "Legacy projection only; assignment provenance was not inferred", null);
    }
}
