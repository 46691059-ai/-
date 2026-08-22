package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskAssignmentSnapshotEntity;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class WorkflowTaskAssignmentSnapshotEntityMapper {
    private WorkflowTaskAssignmentSnapshotEntityMapper() {
    }

    static WorkflowTaskAssignmentSnapshotEntity toEntity(AssignmentSnapshot domain) {
        WorkflowTaskAssignmentSnapshotEntity entity = new WorkflowTaskAssignmentSnapshotEntity();
        entity.setId(domain.id());
        entity.setTaskId(domain.taskId());
        entity.setInstanceId(domain.instanceId());
        entity.setVersionId(domain.versionId());
        entity.setNodeId(domain.nodeId());
        entity.setNodeExecutionId(domain.nodeExecutionId());
        entity.setStrategyType(domain.strategyType().name());
        entity.setTargetType(domain.targetType().name());
        entity.setTargetSnapshot(domain.targetSnapshot());
        entity.setResolvedUsers(serializeUsers(domain.resolvedUserIds()));
        entity.setResolvedUserCount(domain.resolvedUserIds().size());
        entity.setResolveTime(domain.resolveTime());
        entity.setAuditInfo(domain.auditInfo());
        entity.setTraceId(domain.traceId());
        entity.setVersion(domain.version());
        return entity;
    }

    static AssignmentSnapshot toDomain(WorkflowTaskAssignmentSnapshotEntity entity) {
        try {
            List<Long> users = parseUsers(entity.getResolvedUsers());
            if (!Integer.valueOf(users.size()).equals(entity.getResolvedUserCount())) {
                throw new IllegalArgumentException("resolved user count does not match payload");
            }
            return new AssignmentSnapshot(entity.getId(), entity.getTaskId(), entity.getInstanceId(),
                    entity.getVersionId(), entity.getNodeId(), entity.getNodeExecutionId(),
                    AssignmentStrategy.Type.valueOf(entity.getStrategyType()),
                    AssignmentStrategy.Type.valueOf(entity.getTargetType()),
                    entity.getTargetSnapshot(), users, entity.getResolveTime(), entity.getAuditInfo(),
                    entity.getTraceId(), entity.getVersion());
        } catch (RuntimeException exception) {
            BusinessException corrupt = new BusinessException(
                    "B2620", "workflow_task_assignment_snapshot contains invalid data");
            corrupt.initCause(exception);
            throw corrupt;
        }
    }

    private static String serializeUsers(List<Long> users) {
        return users.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static List<Long> parseUsers(String value) {
        if (value == null || value.length() < 3 || value.charAt(0) != '['
                || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("resolved users payload is invalid");
        }
        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) throw new IllegalArgumentException("resolved users payload is empty");
        return Arrays.stream(content.split(","))
                .map(String::trim).map(Long::valueOf).toList();
    }
}
