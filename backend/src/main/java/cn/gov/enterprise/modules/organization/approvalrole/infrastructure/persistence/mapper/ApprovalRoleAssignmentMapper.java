package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleAssignmentEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ApprovalRoleAssignmentMapper extends BaseMapperX<ApprovalRoleAssignmentEntity> {
    @Select("SELECT * FROM approval_role_assignment WHERE id=#{id} AND deleted=0 FOR UPDATE")
    ApprovalRoleAssignmentEntity selectByIdForUpdate(@Param("id") long id);
    @Select("SELECT * FROM approval_role_assignment WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_code=#{roleCode} AND deleted=0 AND status='ACTIVE' AND effective_from<=#{effectiveAt} AND (effective_to IS NULL OR effective_to>#{effectiveAt}) ORDER BY user_id,id")
    List<ApprovalRoleAssignmentEntity> selectEffective(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleCode") String roleCode,@Param("effectiveAt") LocalDateTime effectiveAt);
    @Select("<script>SELECT * FROM approval_role_assignment WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_id=#{roleId} AND user_id=#{userId} AND source_system=#{sourceSystem} AND source_reference=#{sourceReference} AND deleted=0 AND status='ACTIVE' AND (effective_to IS NULL OR #{effectiveFrom}&lt;effective_to) AND (#{effectiveTo} IS NULL OR effective_from&lt;#{effectiveTo}) FOR UPDATE</script>")
    List<ApprovalRoleAssignmentEntity> selectOverlappingForUpdate(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleId") long roleId,@Param("userId") long userId,@Param("sourceSystem") String sourceSystem,@Param("sourceReference") String sourceReference,@Param("effectiveFrom") LocalDateTime effectiveFrom,@Param("effectiveTo") LocalDateTime effectiveTo);
    @Select("SELECT * FROM approval_role_assignment WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_code=#{roleCode} AND deleted=0 ORDER BY effective_from,id")
    List<ApprovalRoleAssignmentEntity> selectHistory(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleCode") String roleCode);
    @Update("UPDATE approval_role_assignment SET effective_to=#{effectiveTo},status=#{status},update_by=#{actor},update_time=#{updatedTime},version=version+1 WHERE id=#{id} AND deleted=0 AND version=#{expectedVersion}")
    int updateGoverned(@Param("id") long id,@Param("effectiveTo") LocalDateTime effectiveTo,@Param("status") String status,@Param("actor") String actor,@Param("updatedTime") LocalDateTime updatedTime,@Param("expectedVersion") int expectedVersion);
}
