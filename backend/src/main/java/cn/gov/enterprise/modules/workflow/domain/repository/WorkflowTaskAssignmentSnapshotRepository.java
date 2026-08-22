package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import java.util.Optional;

public interface WorkflowTaskAssignmentSnapshotRepository {
    Optional<AssignmentSnapshot> findByTaskId(Long taskId);
    void save(AssignmentSnapshot snapshot);
}
