package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {
    private final WorkflowDefinitionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowDefinitionRepositoryImpl(WorkflowDefinitionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public boolean existsByEnterpriseIdAndCode(Long enterpriseId, String definitionCode) {
        return mapper.selectCount(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getEnterpriseId, enterpriseId)
                .eq(WorkflowDefinitionEntity::getDefinitionCode, definitionCode)) > 0;
    }

    @Override
    public Optional<WorkflowDefinition> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override
    public Optional<WorkflowDefinition> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override
    public void save(WorkflowDefinition definition) {
        WorkflowDefinitionEntity entity = WorkflowEntityMapper.toEntity(definition);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2500", "workflow definition save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2509", "workflow definition code already exists in enterprise");
        }
    }

    @Override
    public boolean updateCurrentVersion(WorkflowDefinition definition, int expectedVersion) {
        return mapper.updateCurrentVersion(definition.id(), definition.currentVersionId(),
                definition.status().name(), audit.operator(), expectedVersion) == 1;
    }
}
