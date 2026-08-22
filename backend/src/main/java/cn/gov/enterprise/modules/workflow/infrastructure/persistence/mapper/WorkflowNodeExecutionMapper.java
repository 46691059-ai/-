package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeExecutionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowNodeExecutionMapper extends BaseMapperX<WorkflowNodeExecutionEntity> {
    @Select("SELECT * FROM workflow_node_execution WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkflowNodeExecutionEntity selectActiveByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE workflow_node_execution SET status = #{entity.status}, result = #{entity.result}, "
            + "activated_time = #{entity.activatedTime}, completed_by = #{entity.completedBy}, "
            + "completed_time = #{entity.completedTime}, failure_code = #{entity.failureCode}, "
            + "updated_by = #{operator}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{entity.id} AND deleted = 0 AND version = #{expectedVersion}")
    int updateState(@Param("entity") WorkflowNodeExecutionEntity entity,
                    @Param("expectedVersion") int expectedVersion,
                    @Param("operator") String operator);
}
