package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityEvidenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper public interface WorkflowRoleRealtimeEligibilityEvidenceMapper extends BaseMapper<WorkflowRoleRealtimeEligibilityEvidenceEntity> {
    @Select("SELECT * FROM workflow_role_realtime_eligibility_evidence WHERE id=#{id} FOR UPDATE")
    WorkflowRoleRealtimeEligibilityEvidenceEntity selectByIdForUpdate(long id);
}
