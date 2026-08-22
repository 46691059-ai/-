package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowInstanceRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowInstanceRepositoryImpl implements WorkflowInstanceRepository {
    private final WorkflowInstanceMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowInstanceRepositoryImpl(WorkflowInstanceMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowInstance> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowInstance> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowInstance> findByEnterpriseIdAndIdempotencyKey(
            Long enterpriseId, String idempotencyKey) {
        WorkflowInstanceEntity entity = mapper.selectOne(new LambdaQueryWrapper<WorkflowInstanceEntity>()
                .eq(WorkflowInstanceEntity::getEnterpriseId, enterpriseId)
                .eq(WorkflowInstanceEntity::getIdempotencyKey, idempotencyKey));
        return Optional.ofNullable(entity).map(WorkflowEntityMapper::toDomain);
    }

    @Override public void save(WorkflowInstance instance) {
        WorkflowInstanceEntity entity = WorkflowEntityMapper.toEntity(instance);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2510", "workflow instance save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2519", "workflow start conflicts with an existing instance");
        }
    }

    @Override public void update(WorkflowInstance instance) {
        WorkflowInstanceEntity entity = WorkflowEntityMapper.toEntity(instance);
        audit.touch(entity);
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException("B2529", "workflow instance was changed concurrently");
        }
    }
}
