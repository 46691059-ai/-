package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("approval_role_revision_head")
public class ApprovalRoleRevisionHeadEntity extends BaseIdEntity {
    private String enterpriseId; private Long organizationId; private String roleCode;
    private Long currentRevision; private String currentResultHash;
}
