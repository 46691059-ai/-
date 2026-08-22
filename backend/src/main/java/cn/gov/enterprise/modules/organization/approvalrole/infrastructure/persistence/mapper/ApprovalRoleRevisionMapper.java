package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleRevisionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ApprovalRoleRevisionMapper extends BaseMapperX<ApprovalRoleRevisionEntity> {
    @Select("SELECT * FROM approval_role_revision WHERE enterprise_id=#{enterpriseId} AND organization_id=#{organizationId} AND role_code=#{roleCode} AND deleted=0 ORDER BY revision")
    List<ApprovalRoleRevisionEntity> selectHistory(@Param("enterpriseId") String enterpriseId,@Param("organizationId") long organizationId,@Param("roleCode") String roleCode);
}
