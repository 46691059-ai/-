package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRealtimeEligibilityEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRealtimeEligibilityEvidenceRepositoryImpl implements RoleRealtimeEligibilityEvidenceRepository {
    private final WorkflowRoleRealtimeEligibilityEvidenceMapper headers;
    private final WorkflowRoleRealtimeEligibilityValidatorEvidenceMapper validators;
    private final WorkflowRoleRealtimeEligibilityCapabilityEvidenceMapper capabilities;
    private final WorkflowRoleRealtimeEligibilityEventMapper events;
    private final WorkflowPersistenceAudit audit;

    public RoleRealtimeEligibilityEvidenceRepositoryImpl(WorkflowRoleRealtimeEligibilityEvidenceMapper headers,
            WorkflowRoleRealtimeEligibilityValidatorEvidenceMapper validators,
            WorkflowRoleRealtimeEligibilityCapabilityEvidenceMapper capabilities,
            WorkflowRoleRealtimeEligibilityEventMapper events, WorkflowPersistenceAudit audit) {
        this.headers=headers; this.validators=validators; this.capabilities=capabilities; this.events=events; this.audit=audit;
    }

    @Override public Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByRequestId(String requestId) {
        return find(new LambdaQueryWrapper<WorkflowRoleRealtimeEligibilityEvidenceEntity>()
                .eq(WorkflowRoleRealtimeEligibilityEvidenceEntity::getEligibilityRequestId,requestId));
    }
    @Override public Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByClaimAttempt(
            long taskId,long userId,String requestId,int attemptNo) {
        return find(new LambdaQueryWrapper<WorkflowRoleRealtimeEligibilityEvidenceEntity>()
                .eq(WorkflowRoleRealtimeEligibilityEvidenceEntity::getTaskId,taskId)
                .eq(WorkflowRoleRealtimeEligibilityEvidenceEntity::getCandidateUserId,userId)
                .eq(WorkflowRoleRealtimeEligibilityEvidenceEntity::getClaimRequestId,requestId)
                .eq(WorkflowRoleRealtimeEligibilityEvidenceEntity::getAttemptNo,attemptNo));
    }
    private Optional<RoleRealtimeEligibilityPersistenceBundle.Header> find(
            LambdaQueryWrapper<WorkflowRoleRealtimeEligibilityEvidenceEntity> q) {
        return Optional.ofNullable(headers.selectOne(q)).map(RoleRealtimeEligibilityEvidenceEntityMapper::header);
    }
    @Override public void insert(RoleRealtimeEligibilityPersistenceBundle bundle) {
        try {
            var h=RoleRealtimeEligibilityEvidenceEntityMapper.header(bundle.header()); audit.initialize(h);
            if(headers.insert(h)!=1) throw new BusinessException("B26161","eligibility header insert failed");
            bundle.validators().forEach(v->{var e=RoleRealtimeEligibilityEvidenceEntityMapper.validator(v); audit.initialize(e); validators.insert(e);});
            bundle.capabilities().forEach(c->{var e=RoleRealtimeEligibilityEvidenceEntityMapper.capability(c); audit.initialize(e); capabilities.insert(e);});
            var event=RoleRealtimeEligibilityEvidenceEntityMapper.event(bundle.initialEvent()); audit.initialize(event); events.insert(event);
        } catch (DuplicateKeyException ex) { throw new BusinessException("B26162","eligibility evidence already exists"); }
    }

    @Override public Optional<RoleRealtimeEligibilityPersistenceBundle.Header> findByIdForUpdate(long evidenceId) {
        return Optional.ofNullable(headers.selectByIdForUpdate(evidenceId))
                .map(RoleRealtimeEligibilityEvidenceEntityMapper::header);
    }

    @Override public Optional<RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent> findLatestEvent(long evidenceId) {
        return Optional.ofNullable(events.selectLatest(evidenceId))
                .map(RoleRealtimeEligibilityEvidenceEntityMapper::event);
    }

    @Override public void appendEvent(RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent event) {
        var entity=RoleRealtimeEligibilityEvidenceEntityMapper.event(event); audit.initialize(entity);
        try {
            if(events.insert(entity)!=1) throw new BusinessException("B26164","eligibility event insert failed");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("B26165","eligibility evidence was consumed concurrently");
        }
    }
}
