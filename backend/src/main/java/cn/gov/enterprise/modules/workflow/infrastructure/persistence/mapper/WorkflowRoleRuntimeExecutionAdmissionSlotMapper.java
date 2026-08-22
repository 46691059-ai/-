package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionSlotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkflowRoleRuntimeExecutionAdmissionSlotMapper extends BaseMapper<WorkflowRoleRuntimeExecutionAdmissionSlotEntity> {
    @Select("SELECT * FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=#{id} FOR UPDATE")
    WorkflowRoleRuntimeExecutionAdmissionSlotEntity selectForUpdate(@Param("id") Long id);

    @Update("UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=#{admissionRowId}, active_admission_id=#{admissionId}, active_token=SHA2(CONCAT(active_token,':OCCUPY:',#{admissionId},':',version+1),256), slot_status='OCCUPIED', version=version+1, updated_by=#{operator}, updated_time=CURRENT_TIMESTAMP(3) WHERE candidate_snapshot_row_id=#{candidateId} AND version=#{expectedVersion} AND active_token=#{expectedToken} AND active_admission_row_id IS NULL")
    int occupy(@Param("candidateId") Long candidateId, @Param("expectedVersion") int expectedVersion,
            @Param("expectedToken") String expectedToken,
            @Param("admissionRowId") Long admissionRowId, @Param("admissionId") String admissionId,
            @Param("operator") String operator);

    @Update("UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=NULL, active_admission_id=NULL, active_token=SHA2(CONCAT(active_token,':RELEASE:',version+1),256), slot_status='VACANT', version=version+1, updated_by=#{operator}, updated_time=CURRENT_TIMESTAMP(3) WHERE candidate_snapshot_row_id=#{candidateId} AND version=#{expectedVersion} AND active_token=#{expectedToken} AND active_admission_row_id IS NOT NULL")
    int release(@Param("candidateId") Long candidateId, @Param("expectedVersion") int expectedVersion,
            @Param("expectedToken") String expectedToken,
            @Param("operator") String operator);
}
