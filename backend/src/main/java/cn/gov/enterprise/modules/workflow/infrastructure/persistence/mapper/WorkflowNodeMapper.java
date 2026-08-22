package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface WorkflowNodeMapper extends BaseMapperX<WorkflowNodeEntity> {
    @Update("UPDATE workflow_node SET deleted = 1, delete_token = id, "
            + "updated_by = #{operator}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE version_id = #{versionId} AND deleted = 0")
    int logicalDeleteActiveByVersionId(@Param("versionId") Long versionId,
                                       @Param("operator") String operator);
}
