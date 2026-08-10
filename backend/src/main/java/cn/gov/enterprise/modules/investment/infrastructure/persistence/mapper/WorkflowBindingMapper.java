package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;
import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
public interface WorkflowBindingMapper extends BaseMapperX<WorkflowBindingEntity> {
    @Select("SELECT COALESCE(MAX(attempt_no),0)+1 FROM investment_workflow_binding WHERE decision_id=#{id} AND deleted=0")
    int nextAttempt(@Param("id") Long decisionId);
    @Select("SELECT * FROM investment_workflow_binding WHERE decision_id=#{id} AND deleted=0 ORDER BY attempt_no DESC LIMIT 1")
    WorkflowBindingEntity selectCurrent(@Param("id") Long decisionId);
    @Select("SELECT * FROM investment_workflow_binding WHERE workflow_instance_id=#{id} AND deleted=0 FOR UPDATE")
    WorkflowBindingEntity selectByInstanceForUpdate(@Param("id") String instanceId);
}
