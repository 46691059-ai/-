package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleDirectoryProviderAuditEntity;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface ApprovalRoleDirectoryProviderAuditMapper extends BaseMapperX<ApprovalRoleDirectoryProviderAuditEntity> {
    @Select("SELECT * FROM approval_role_directory_provider_audit ORDER BY created_at DESC, audit_id DESC LIMIT #{limit}")
    List<ApprovalRoleDirectoryProviderAuditEntity> selectRecent(int limit);
}
