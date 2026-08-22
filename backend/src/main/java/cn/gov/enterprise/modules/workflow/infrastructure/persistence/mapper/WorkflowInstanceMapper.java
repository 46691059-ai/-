package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowInstanceMapper extends BaseMapperX<WorkflowInstanceEntity> {
    @Select("SELECT * FROM workflow_instance WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkflowInstanceEntity selectActiveByIdForUpdate(@Param("id") Long id);
}
