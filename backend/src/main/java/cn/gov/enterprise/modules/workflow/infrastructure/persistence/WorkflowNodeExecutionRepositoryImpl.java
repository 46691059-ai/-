package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeExecutionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeExecutionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowNodeExecutionRepositoryImpl implements WorkflowNodeExecutionRepository {
    private final WorkflowNodeExecutionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowNodeExecutionRepositoryImpl(
            WorkflowNodeExecutionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowNodeExecution> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowNodeExecution> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id))
                .map(WorkflowEntityMapper::toDomain);
    }

    @Override public Optional<WorkflowNodeExecution> findByInstanceIdAndNodeIdAndVisitNo(
            Long instanceId, Long nodeId, int visitNo) {
        WorkflowNodeExecutionEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<WorkflowNodeExecutionEntity>()
                        .eq(WorkflowNodeExecutionEntity::getInstanceId, instanceId)
                        .eq(WorkflowNodeExecutionEntity::getNodeId, nodeId)
                        .eq(WorkflowNodeExecutionEntity::getVisitNo, visitNo));
        return Optional.ofNullable(entity).map(WorkflowEntityMapper::toDomain);
    }

    @Override public List<WorkflowNodeExecution> findByInstanceId(Long instanceId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowNodeExecutionEntity>()
                        .eq(WorkflowNodeExecutionEntity::getInstanceId, instanceId)
                        .orderByAsc(WorkflowNodeExecutionEntity::getEnteredTime,
                                WorkflowNodeExecutionEntity::getId))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }

    @Override public void save(WorkflowNodeExecution execution) {
        WorkflowNodeExecutionEntity entity = WorkflowEntityMapper.toEntity(execution);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2600", "workflow node execution save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2609", "workflow node visit already exists");
        }
    }

    @Override public boolean updateState(WorkflowNodeExecution execution, int expectedVersion) {
        WorkflowNodeExecutionEntity entity = WorkflowEntityMapper.toEntity(execution);
        return mapper.updateState(entity, expectedVersion, audit.operator()) == 1;
    }
}
