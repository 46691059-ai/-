package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("approval_role")
public class ApprovalRoleEntity extends BaseIdEntity {
    private String enterpriseId; private String roleCode; private String roleName;
    private String roleType; private String organizationScopeType; private String status;
    private String description;
}
