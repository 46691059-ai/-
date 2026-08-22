package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("approval_role_assignment")
public class ApprovalRoleAssignmentEntity extends BaseIdEntity {
    private String enterpriseId; private Long organizationId; private Long roleId;
    private String roleCode; private Long userId; private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo; private String status; private String sourceType;
    private String sourceSystem; private String sourceReference; private String sourceReferenceHash;
    private Integer evidencePriority; private LocalDateTime recordedAt; private String assignmentKeyHash;
}
