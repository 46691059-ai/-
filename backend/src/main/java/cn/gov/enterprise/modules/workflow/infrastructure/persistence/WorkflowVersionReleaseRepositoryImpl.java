package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionReleaseMapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowVersionReleaseRepositoryImpl implements WorkflowVersionReleaseRepository {
    private final WorkflowVersionReleaseMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowVersionReleaseRepositoryImpl(WorkflowVersionReleaseMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void save(WorkflowVersionRelease release) {
        WorkflowVersionReleaseEntity entity = WorkflowEntityMapper.toEntity(release);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2500", "workflow release audit save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2509", "workflow version already has a publication audit record");
        }
    }

    @Override
    public Optional<WorkflowVersionRelease> findByDefinitionIdAndPublishedVersionId(
            Long definitionId, Long publishedVersionId) {
        return Optional.ofNullable(mapper.selectByDefinitionIdAndPublishedVersionId(
                        definitionId, publishedVersionId))
                .map(WorkflowEntityMapper::toDomain);
    }
}
