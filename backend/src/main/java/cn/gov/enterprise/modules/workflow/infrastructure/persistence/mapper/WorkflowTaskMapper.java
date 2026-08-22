package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowTaskMapper extends BaseMapperX<WorkflowTaskEntity> {
    @Select("SELECT * FROM workflow_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkflowTaskEntity selectActiveByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE workflow_task SET assignee_user_id = #{assigneeUserId}, "
            + "claimed_time = #{claimedTime}, status = 'CLAIMED', "
            + "updated_by = #{operator}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND assignment_mode = 'CANDIDATE_POOL' "
            + "AND status = 'PENDING' AND assignee_user_id IS NULL AND claimed_time IS NULL "
            + "AND version = #{expectedVersion}")
    int claim(@Param("id") Long id, @Param("assigneeUserId") Long assigneeUserId,
              @Param("claimedTime") java.time.LocalDateTime claimedTime,
              @Param("expectedVersion") int expectedVersion,
              @Param("operator") String operator);
}
