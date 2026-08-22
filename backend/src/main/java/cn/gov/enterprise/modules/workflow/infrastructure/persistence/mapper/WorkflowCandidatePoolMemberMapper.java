package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolMemberEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowCandidatePoolMemberMapper
        extends BaseMapperX<WorkflowCandidatePoolMemberEntity> {
    @Select("SELECT * FROM workflow_task_candidate_member "
            + "WHERE pool_id = #{poolId} AND candidate_user_id = #{userId} "
            + "AND deleted = 0 FOR UPDATE")
    WorkflowCandidatePoolMemberEntity selectByPoolAndUserForUpdate(
            @Param("poolId") Long poolId, @Param("userId") Long userId);
}
