package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionNodeResolverBindingMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class VersionNodeResolverBindingRepositoryImpl
        implements VersionNodeResolverBindingRepository {
    private final WorkflowVersionNodeResolverBindingMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public VersionNodeResolverBindingRepositoryImpl(
            WorkflowVersionNodeResolverBindingMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void add(VersionNodeResolverBinding binding) {
        WorkflowVersionNodeResolverBindingEntity entity =
                WorkflowVersionResolverBindingEntityMapper.toEntity(binding);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2621", "Version resolver binding save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2629", "Version resolver binding order already exists");
        }
    }

    @Override
    public void update(VersionNodeResolverBinding binding) {
        WorkflowVersionNodeResolverBindingEntity entity =
                WorkflowVersionResolverBindingEntityMapper.toEntity(binding);
        audit.touch(entity);
        if (mapper.updateActive(entity, binding.version()) != 1) {
            throw new BusinessException("B2629", "Version resolver binding changed concurrently");
        }
    }

    @Override
    public boolean logicalDelete(Long id, int expectedVersion) {
        return mapper.logicalDelete(id, expectedVersion, audit.operator()) == 1;
    }

    @Override
    public Optional<VersionNodeResolverBinding> findById(Long id) {
        return Optional.ofNullable(mapper.selectActiveById(id))
                .map(WorkflowVersionResolverBindingEntityMapper::toDomain);
    }

    @Override
    public List<VersionNodeResolverBinding> findByVersionId(Long definitionVersionId) {
        return mapper.selectActiveByVersionId(definitionVersionId).stream()
                .map(WorkflowVersionResolverBindingEntityMapper::toDomain).toList();
    }

    @Override
    public List<VersionNodeResolverBinding> findByVersionIdAndNodeId(
            Long definitionVersionId, Long nodeId) {
        return mapper.selectActiveByVersionIdAndNodeId(definitionVersionId, nodeId).stream()
                .map(WorkflowVersionResolverBindingEntityMapper::toDomain).toList();
    }
}
