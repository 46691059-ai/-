package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowCandidatePoolMapper extends BaseMapperX<WorkflowCandidatePoolEntity> {
    @Select("SELECT * FROM workflow_task_candidate_pool "
            + "WHERE task_id = #{taskId} AND deleted = 0 FOR UPDATE")
    WorkflowCandidatePoolEntity selectByTaskIdForUpdate(@Param("taskId") Long taskId);

    @Update("UPDATE workflow_task_candidate_pool SET status = 'CLAIMED', "
            + "updated_by = #{operator}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND status = 'AVAILABLE' "
            + "AND version = #{expectedVersion}")
    int claim(@Param("id") Long id, @Param("expectedVersion") int expectedVersion,
              @Param("operator") String operator);
}
