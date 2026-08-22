package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowDefinitionMapper extends BaseMapperX<WorkflowDefinitionEntity> {
    @Select("SELECT * FROM workflow_definition WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkflowDefinitionEntity selectActiveByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE workflow_definition SET current_version_id = #{currentVersionId}, status = #{status}, "
            + "updated_by = #{updatedBy}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND version = #{expectedVersion}")
    int updateCurrentVersion(@Param("id") Long id, @Param("currentVersionId") Long currentVersionId,
            @Param("status") String status, @Param("updatedBy") String updatedBy,
            @Param("expectedVersion") int expectedVersion);
}
