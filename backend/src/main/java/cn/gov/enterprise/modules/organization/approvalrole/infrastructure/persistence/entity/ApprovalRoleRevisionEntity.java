package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("approval_role_revision")
public class ApprovalRoleRevisionEntity extends BaseIdEntity {
    private String enterpriseId; private Long organizationId; private String roleCode;
    private Long revision; private String resultHash; private String changeType;
    private String changeReason; private LocalDateTime effectiveFrom; private LocalDateTime affectedFrom;
    private LocalDateTime affectedTo; private String correctionReference; private LocalDateTime publishedAt;
    private String publishedBy; private Long previousRevision; private String previousResultHash;
}
