package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionReleaseMapper;
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
        WorkflowVersionReleaseEntity entity = new WorkflowVersionReleaseEntity();
        entity.setId(release.id());
        entity.setDefinitionId(release.definitionId());
        entity.setPreviousVersionId(release.previousVersionId());
        entity.setPublishedVersionId(release.publishedVersionId());
        entity.setPublishedVersionNo(release.publishedVersionNo());
        entity.setContentHash(release.contentHash());
        entity.setEngineMode(release.engineMode().name());
        entity.setContentHashAlgorithm(release.contentHashAlgorithm().name());
        entity.setOperatorUserId(release.operatorUserId());
        entity.setOperatorOrgId(release.operatorOrgId());
        entity.setPublishedTime(release.publishedTime());
        entity.setTraceId(release.traceId());
        entity.setValidationSummary(release.validationSummary());
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2500", "workflow release audit save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2509", "workflow version already has a publication audit record");
        }
    }
}
