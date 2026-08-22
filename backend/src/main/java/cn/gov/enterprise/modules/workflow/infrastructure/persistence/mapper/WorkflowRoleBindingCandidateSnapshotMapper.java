package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingCandidateSnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRoleBindingCandidateSnapshotMapper
        extends BaseMapper<WorkflowRoleBindingCandidateSnapshotEntity> {
    @Select("SELECT * FROM workflow_role_binding_candidate_snapshot WHERE snapshot_id=#{snapshotId} AND deleted=0 FOR UPDATE")
    WorkflowRoleBindingCandidateSnapshotEntity selectForUpdate(@Param("snapshotId") String snapshotId);
}
