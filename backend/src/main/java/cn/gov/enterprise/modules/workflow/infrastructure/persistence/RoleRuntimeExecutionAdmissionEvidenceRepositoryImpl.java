package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeExecutionAdmissionEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionEvidenceRecord;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeExecutionAdmissionEvidenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeExecutionAdmissionEvidenceRepositoryImpl implements RoleRuntimeExecutionAdmissionEvidenceRepository {
    private final WorkflowRoleRuntimeExecutionAdmissionEvidenceMapper mapper; private final WorkflowPersistenceAudit audit;
    public RoleRuntimeExecutionAdmissionEvidenceRepositoryImpl(WorkflowRoleRuntimeExecutionAdmissionEvidenceMapper mapper,WorkflowPersistenceAudit audit){this.mapper=mapper;this.audit=audit;}
    @Override public void appendAll(List<RoleRuntimeExecutionAdmissionEvidenceRecord> records){ for(var r:records){var e=RoleRuntimeExecutionAdmissionEntityMapper.toEntity(r);audit.initialize(e);if(mapper.insert(e)!=1)throw new BusinessException("B26153","execution admission evidence append failed");}}
    @Override public List<RoleRuntimeExecutionAdmissionEvidenceRecord> findByAdmissionId(String id){return mapper.selectList(new LambdaQueryWrapper<WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity>().eq(WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity::getAdmissionId,id).orderByAsc(WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity::getSequenceNo)).stream().map(RoleRuntimeExecutionAdmissionEntityMapper::toDomain).toList();}
}
