package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTransitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTransitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowTransitionRepositoryImpl implements WorkflowTransitionRepository {
    private final WorkflowTransitionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowTransitionRepositoryImpl(WorkflowTransitionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowTransition> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public List<WorkflowTransition> findByVersionId(Long versionId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowTransitionEntity>()
                        .eq(WorkflowTransitionEntity::getVersionId, versionId)
                        .orderByAsc(WorkflowTransitionEntity::getPriority, WorkflowTransitionEntity::getId))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }

    @Override public List<WorkflowTransition> findOutgoing(
            Long versionId, Long fromNodeId, WorkflowTransition.TriggerType triggerType) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowTransitionEntity>()
                        .eq(WorkflowTransitionEntity::getVersionId, versionId)
                        .eq(WorkflowTransitionEntity::getFromNodeId, fromNodeId)
                        .eq(WorkflowTransitionEntity::getTriggerType, triggerType.name())
                        .eq(WorkflowTransitionEntity::getEnabled, 1)
                        .orderByAsc(WorkflowTransitionEntity::getPriority, WorkflowTransitionEntity::getId))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }

    @Override public void save(WorkflowTransition transition) {
        WorkflowTransitionEntity entity = WorkflowEntityMapper.toEntity(transition);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2600", "workflow transition save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2609", "workflow transition code or route is duplicated");
        }
    }
}
