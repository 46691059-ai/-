package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeActivationEventRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvent;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationState;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationEventEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeActivationEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeActivationEventRepositoryImpl implements RoleRuntimeActivationEventRepository {
    private final RoleRuntimeActivationEventMapper mapper;
    private final WorkflowPersistenceAudit audit;
    public RoleRuntimeActivationEventRepositoryImpl(RoleRuntimeActivationEventMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }
    @Override public Optional<RoleRuntimeActivationEvent> findByExactScope(CanaryScope scope) {
        var rows = mapper.selectList(scopeQuery(scope).last("LIMIT 2"));
        if (rows.size() > 1) throw new IllegalStateException("ambiguous ROLE Runtime activation state");
        return rows.stream().findFirst().map(this::toDomain);
    }
    @Override public long countByExactScope(CanaryScope scope) { return mapper.selectCount(scopeQuery(scope)); }
    @Override public boolean append(RoleRuntimeActivationEvent event) {
        var entity = toEntity(event); audit.initialize(entity);
        try { return mapper.insert(entity) == 1; }
        catch (DuplicateKeyException duplicate) { return false; }
    }
    private LambdaQueryWrapper<RoleRuntimeActivationEventEntity> scopeQuery(CanaryScope s) {
        return new LambdaQueryWrapper<RoleRuntimeActivationEventEntity>()
                .eq(RoleRuntimeActivationEventEntity::getEnterpriseId,s.enterpriseId())
                .eq(RoleRuntimeActivationEventEntity::getOrganizationId,s.organizationId())
                .eq(RoleRuntimeActivationEventEntity::getDefinitionId,s.definitionId())
                .eq(RoleRuntimeActivationEventEntity::getDefinitionVersionId,s.definitionVersionId())
                .eq(RoleRuntimeActivationEventEntity::getNodeId,s.nodeId())
                .eq(RoleRuntimeActivationEventEntity::getRoleCode,s.roleCode())
                .eq(RoleRuntimeActivationEventEntity::getEventType,RoleRuntimeActivationEvent.EVENT_TYPE)
                .eq(RoleRuntimeActivationEventEntity::getDeleted,0);
    }
    private RoleRuntimeActivationEventEntity toEntity(RoleRuntimeActivationEvent e) {
        var x=new RoleRuntimeActivationEventEntity(); x.setId(e.id());x.setEventId(e.eventId());
        x.setEnterpriseId(e.scope().enterpriseId());x.setOrganizationId(e.scope().organizationId());
        x.setDefinitionId(e.scope().definitionId());x.setDefinitionVersionId(e.scope().definitionVersionId());
        x.setNodeId(e.scope().nodeId());x.setRoleCode(e.scope().roleCode());x.setEventType(e.eventType());
        x.setSequence(e.sequence());x.setRevision(e.revision());x.setPreviousState(e.previousState().name());
        x.setResultingState(e.resultingState().name());x.setAuthorizationId(e.authorizationId());
        x.setAuthorizationType(e.authorizationType());x.setAuthorizationCommit(e.authorizationCommit());
        x.setObservationEvidenceCommit(e.observationEvidenceCommit());
        x.setRuntimeEnablementEvidenceCommit(e.runtimeEnablementEvidenceCommit());
        x.setRuntimeReleaseCommit(e.runtimeReleaseCommit());x.setRuntimeReleaseTag(e.runtimeReleaseTag());
        x.setDirectoryResultHash(e.directoryResultHash());x.setVersionBindingHash(e.versionBindingHash());
        x.setManifestHash(e.manifestHash());x.setContentHash(e.contentHash());
        x.setStructuralFingerprint(e.structuralFingerprint());x.setActorType(e.actorType());x.setActorId(e.actorId());
        x.setOccurredAt(java.time.LocalDateTime.ofInstant(e.occurredAt(),ZoneOffset.UTC));return x;
    }
    private RoleRuntimeActivationEvent toDomain(RoleRuntimeActivationEventEntity e) {
        return new RoleRuntimeActivationEvent(e.getId(),e.getEventId(),new CanaryScope(e.getEnterpriseId(),e.getOrganizationId(),e.getDefinitionId(),e.getDefinitionVersionId(),e.getNodeId(),e.getRoleCode()),e.getEventType(),e.getSequence(),e.getRevision(),RoleRuntimeActivationState.valueOf(e.getPreviousState()),RoleRuntimeActivationState.valueOf(e.getResultingState()),e.getAuthorizationId(),e.getAuthorizationType(),e.getAuthorizationCommit(),e.getObservationEvidenceCommit(),e.getRuntimeEnablementEvidenceCommit(),e.getRuntimeReleaseCommit(),e.getRuntimeReleaseTag(),e.getDirectoryResultHash(),e.getVersionBindingHash(),e.getManifestHash(),e.getContentHash(),e.getStructuralFingerprint(),e.getActorType(),e.getActorId(),e.getOccurredAt().toInstant(ZoneOffset.UTC));
    }
}
