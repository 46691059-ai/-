package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskAssignmentSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskAssignmentSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowTaskAssignmentSnapshotRepositoryImpl
        implements WorkflowTaskAssignmentSnapshotRepository {
    private final WorkflowTaskAssignmentSnapshotMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowTaskAssignmentSnapshotRepositoryImpl(
            WorkflowTaskAssignmentSnapshotMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public Optional<AssignmentSnapshot> findByTaskId(Long taskId) {
        WorkflowTaskAssignmentSnapshotEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<WorkflowTaskAssignmentSnapshotEntity>()
                        .eq(WorkflowTaskAssignmentSnapshotEntity::getTaskId, taskId));
        return Optional.ofNullable(entity).map(WorkflowTaskAssignmentSnapshotEntityMapper::toDomain);
    }

    @Override
    public void save(AssignmentSnapshot snapshot) {
        WorkflowTaskAssignmentSnapshotEntity entity =
                WorkflowTaskAssignmentSnapshotEntityMapper.toEntity(snapshot);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2620", "workflow task assignment snapshot save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2629", "workflow task assignment snapshot already exists");
        }
    }
}
