package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ApprovalRoleMapper extends BaseMapperX<ApprovalRoleEntity> {
    @Select("SELECT * FROM approval_role WHERE enterprise_id=#{enterpriseId} AND role_code=#{roleCode} AND deleted=0 LIMIT 1")
    ApprovalRoleEntity selectByKey(@Param("enterpriseId") String enterpriseId,@Param("roleCode") String roleCode);
    @Select("SELECT * FROM approval_role WHERE enterprise_id=#{enterpriseId} AND role_code=#{roleCode} AND deleted=0 FOR UPDATE")
    ApprovalRoleEntity selectByKeyForUpdate(@Param("enterpriseId") String enterpriseId,@Param("roleCode") String roleCode);
    @Update("UPDATE approval_role SET status=#{status},update_by=#{actor},update_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=#{id} AND deleted=0 AND version=#{expectedVersion}")
    int updateStatus(@Param("id") long id,@Param("status") String status,@Param("actor") String actor,@Param("expectedVersion") int expectedVersion);
}
