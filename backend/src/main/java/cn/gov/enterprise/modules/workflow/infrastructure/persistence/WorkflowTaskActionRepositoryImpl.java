package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTaskAction;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskActionRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskActionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskActionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowTaskActionRepositoryImpl implements WorkflowTaskActionRepository {
    private final WorkflowTaskActionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowTaskActionRepositoryImpl(
            WorkflowTaskActionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public Optional<WorkflowTaskAction> findByTaskIdAndIdempotencyKey(
            Long taskId, String idempotencyKey) {
        WorkflowTaskActionEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<WorkflowTaskActionEntity>()
                        .eq(WorkflowTaskActionEntity::getTaskId, taskId)
                        .eq(WorkflowTaskActionEntity::getIdempotencyKey, idempotencyKey));
        return Optional.ofNullable(entity).map(WorkflowEntityMapper::toDomain);
    }

    @Override
    public void save(WorkflowTaskAction action) {
        WorkflowTaskActionEntity entity = WorkflowEntityMapper.toEntity(action);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2520", "workflow task action save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2529", "workflow task action already exists");
        }
    }
}
