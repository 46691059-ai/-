package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionResolverBindingManifestMapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ResolverBindingManifestRepositoryImpl implements ResolverBindingManifestRepository {
    private final WorkflowVersionResolverBindingManifestMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public ResolverBindingManifestRepositoryImpl(
            WorkflowVersionResolverBindingManifestMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void append(ResolverBindingManifest manifest) {
        WorkflowVersionResolverBindingManifestEntity entity =
                WorkflowVersionResolverBindingEntityMapper.toEntity(manifest);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2621", "Resolver binding Manifest append failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2629", "Resolver binding Manifest already exists");
        }
    }

    @Override
    public Optional<ResolverBindingManifest> findByDefinitionVersionId(Long definitionVersionId) {
        return Optional.ofNullable(mapper.selectByDefinitionVersionId(definitionVersionId))
                .map(WorkflowVersionResolverBindingEntityMapper::toDomain);
    }
}
