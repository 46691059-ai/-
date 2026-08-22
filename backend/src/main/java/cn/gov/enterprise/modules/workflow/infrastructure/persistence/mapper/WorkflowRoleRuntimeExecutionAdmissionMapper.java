package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRoleRuntimeExecutionAdmissionMapper extends BaseMapper<WorkflowRoleRuntimeExecutionAdmissionEntity> {
    @Select("""
            SELECT a.* FROM workflow_role_runtime_execution_admission_slot s
            JOIN workflow_role_runtime_execution_admission a ON a.id=s.active_admission_row_id
            WHERE s.slot_status='OCCUPIED' AND a.binding_hash=#{bindingHash}
              AND a.deleted=0 AND s.deleted=0
            LIMIT 1
            """)
    WorkflowRoleRuntimeExecutionAdmissionEntity selectActiveByBindingHash(String bindingHash);
}
