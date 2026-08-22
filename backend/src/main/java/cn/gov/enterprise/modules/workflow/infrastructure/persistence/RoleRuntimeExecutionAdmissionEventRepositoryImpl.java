package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeExecutionAdmissionEventRepository;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionEvent;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionEventEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeExecutionAdmissionEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeExecutionAdmissionEventRepositoryImpl implements RoleRuntimeExecutionAdmissionEventRepository {
    private final WorkflowRoleRuntimeExecutionAdmissionEventMapper mapper; private final WorkflowPersistenceAudit audit;
    public RoleRuntimeExecutionAdmissionEventRepositoryImpl(WorkflowRoleRuntimeExecutionAdmissionEventMapper mapper,WorkflowPersistenceAudit audit){this.mapper=mapper;this.audit=audit;}
    @Override public void append(RoleRuntimeExecutionAdmissionEvent r){var e=RoleRuntimeExecutionAdmissionEntityMapper.toEntity(r);audit.initialize(e);if(mapper.insert(e)!=1)throw new BusinessException("B26154","execution admission event append failed");}
    @Override public List<RoleRuntimeExecutionAdmissionEvent> findByAdmissionId(String id){return mapper.selectList(new LambdaQueryWrapper<WorkflowRoleRuntimeExecutionAdmissionEventEntity>().eq(WorkflowRoleRuntimeExecutionAdmissionEventEntity::getAdmissionId,id).orderByAsc(WorkflowRoleRuntimeExecutionAdmissionEventEntity::getSequenceNo)).stream().map(RoleRuntimeExecutionAdmissionEntityMapper::toDomain).toList();}
}
