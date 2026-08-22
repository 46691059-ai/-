package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeExecutionAdmissionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.PersistentRoleRuntimeExecutionAdmission;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeExecutionAdmissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeExecutionAdmissionRepositoryImpl implements RoleRuntimeExecutionAdmissionRepository {
    private final WorkflowRoleRuntimeExecutionAdmissionMapper mapper; private final WorkflowPersistenceAudit audit;
    public RoleRuntimeExecutionAdmissionRepositoryImpl(WorkflowRoleRuntimeExecutionAdmissionMapper mapper, WorkflowPersistenceAudit audit) { this.mapper=mapper; this.audit=audit; }
    @Override public void insert(PersistentRoleRuntimeExecutionAdmission admission) {
        var e=RoleRuntimeExecutionAdmissionEntityMapper.toEntity(admission); audit.initialize(e);
        try { if (mapper.insert(e)!=1) throw new BusinessException("B26151","execution admission insert failed"); }
        catch (DuplicateKeyException ex) { throw new BusinessException("B26152","execution admission already exists"); }
    }
    @Override public Optional<PersistentRoleRuntimeExecutionAdmission> findByRequestId(String requestId) { return select(new LambdaQueryWrapper<WorkflowRoleRuntimeExecutionAdmissionEntity>().eq(WorkflowRoleRuntimeExecutionAdmissionEntity::getRequestId,requestId)); }
    @Override public Optional<PersistentRoleRuntimeExecutionAdmission> findByCandidateAndIdempotencyKey(Long candidateId,String key) { return select(new LambdaQueryWrapper<WorkflowRoleRuntimeExecutionAdmissionEntity>().eq(WorkflowRoleRuntimeExecutionAdmissionEntity::getCandidateSnapshotRowId,candidateId).eq(WorkflowRoleRuntimeExecutionAdmissionEntity::getIdempotencyKey,key)); }
    @Override public Optional<PersistentRoleRuntimeExecutionAdmission> findActiveByBindingHash(String bindingHash) {
        return Optional.ofNullable(mapper.selectActiveByBindingHash(bindingHash))
                .map(RoleRuntimeExecutionAdmissionEntityMapper::toDomain);
    }
    private Optional<PersistentRoleRuntimeExecutionAdmission> select(LambdaQueryWrapper<WorkflowRoleRuntimeExecutionAdmissionEntity> q) { return Optional.ofNullable(mapper.selectOne(q)).map(RoleRuntimeExecutionAdmissionEntityMapper::toDomain); }
}
