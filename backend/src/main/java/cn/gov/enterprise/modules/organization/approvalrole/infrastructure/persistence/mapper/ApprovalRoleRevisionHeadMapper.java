package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleRevisionHeadEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

public interface ApprovalRoleRevisionHeadMapper extends BaseMapperX<ApprovalRoleRevisionHeadEntity> {
    @Select("SELECT * FROM approval_role_revision_head WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_code=#{roleCode} AND deleted=0 FOR UPDATE")
    ApprovalRoleRevisionHeadEntity selectForUpdate(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleCode") String roleCode);
    @Select("SELECT * FROM approval_role_revision_head WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_code=#{roleCode} AND deleted=0")
    ApprovalRoleRevisionHeadEntity selectCurrent(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleCode") String roleCode);
    @Select("SELECT * FROM approval_role_revision_head WHERE enterprise_id=#{enterpriseId} AND role_code=#{roleCode} AND deleted=0 ORDER BY organization_id")
    List<ApprovalRoleRevisionHeadEntity> selectByRole(@Param("enterpriseId") String enterpriseId,@Param("roleCode") String roleCode);
    @Update("UPDATE approval_role_revision_head SET current_revision=#{nextRevision},current_result_hash=#{nextHash},update_by=#{actor},update_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=#{id} AND deleted=0 AND version=#{expectedVersion} AND current_revision=#{currentRevision}")
    int advance(@Param("id") long id,@Param("currentRevision") long currentRevision,@Param("nextRevision") long nextRevision,@Param("nextHash") String nextHash,@Param("actor") String actor,@Param("expectedVersion") int expectedVersion);
}
