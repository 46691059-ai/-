package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowVersionRepositoryImpl implements WorkflowVersionRepository {
    private final WorkflowVersionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowVersionRepositoryImpl(WorkflowVersionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowVersion> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }
    @Override public Optional<WorkflowVersion> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id)).map(WorkflowEntityMapper::toDomain);
    }
    @Override public List<WorkflowVersion> findByDefinitionId(Long definitionId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowVersionEntity>()
                        .eq(WorkflowVersionEntity::getDefinitionId, definitionId)
                        .orderByAsc(WorkflowVersionEntity::getVersionNo))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }
    @Override public int nextVersionNo(Long definitionId) { return mapper.selectNextVersionNo(definitionId); }
    @Override public void save(WorkflowVersion workflowVersion) {
        WorkflowVersionEntity entity = WorkflowEntityMapper.toEntity(workflowVersion);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2500", "workflow version save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2509", "workflow version number already exists");
        }
    }

    @Override
    public boolean prepareResolverBindingSnapshot(
            WorkflowVersion workflowVersion, int expectedVersion) {
        return mapper.prepareResolverBindingSnapshot(
                workflowVersion.id(), workflowVersion.resolverBindingManifestHash(),
                workflowVersion.resolverBindingCount(),
                workflowVersion.resolverBindingCanonicalVersion(),
                audit.operator(), expectedVersion) == 1;
    }

    @Override
    public boolean updateState(WorkflowVersion workflowVersion, WorkflowVersion.Status expectedStatus,
                               int expectedVersion) {
        return mapper.updateState(workflowVersion.id(), workflowVersion.status().name(),
                workflowVersion.contentHash(), workflowVersion.effectiveFrom(), workflowVersion.effectiveTo(),
                workflowVersion.publishedBy(), workflowVersion.publishedTime(),
                workflowVersion.resolverBindingModel().name(),
                workflowVersion.resolverBindingManifestHash(), workflowVersion.resolverBindingCount(),
                workflowVersion.resolverBindingCanonicalVersion(), audit.operator(),
                expectedStatus.name(), expectedVersion) == 1;
    }
}
