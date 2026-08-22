package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowTaskRepositoryImpl implements WorkflowTaskRepository {
    private final WorkflowTaskMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowTaskRepositoryImpl(WorkflowTaskMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowTask> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowTask> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowTask> findByNodeExecutionId(Long nodeExecutionId) {
        WorkflowTaskEntity entity = mapper.selectOne(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getNodeExecutionId, nodeExecutionId));
        return Optional.ofNullable(entity).map(WorkflowEntityMapper::toDomain);
    }

    @Override public List<WorkflowTask> findByInstanceId(Long instanceId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowTaskEntity>()
                        .eq(WorkflowTaskEntity::getInstanceId, instanceId)
                        .orderByAsc(WorkflowTaskEntity::getTaskRound, WorkflowTaskEntity::getCreatedTime))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }

    @Override public void save(WorkflowTask task) {
        WorkflowTaskEntity entity = WorkflowEntityMapper.toEntity(task);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2510", "workflow task save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2519", "workflow task already exists");
        }
    }

    @Override public void update(WorkflowTask task) {
        WorkflowTaskEntity entity = WorkflowEntityMapper.toEntity(task);
        audit.touch(entity);
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException("B2529", "workflow task was processed concurrently");
        }
    }

    @Override public boolean claim(WorkflowTask task, int expectedVersion) {
        return mapper.claim(task.id(), task.assigneeUserId(), task.claimedTime(), expectedVersion,
                audit.operator()) == 1;
    }
}
