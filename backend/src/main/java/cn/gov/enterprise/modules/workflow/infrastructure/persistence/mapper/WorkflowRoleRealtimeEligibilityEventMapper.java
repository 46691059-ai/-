package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper public interface WorkflowRoleRealtimeEligibilityEventMapper extends BaseMapper<WorkflowRoleRealtimeEligibilityEventEntity> {
    @Select("SELECT * FROM workflow_role_realtime_eligibility_event WHERE evidence_id=#{evidenceId} ORDER BY sequence_no DESC LIMIT 1")
    WorkflowRoleRealtimeEligibilityEventEntity selectLatest(long evidenceId);
}
