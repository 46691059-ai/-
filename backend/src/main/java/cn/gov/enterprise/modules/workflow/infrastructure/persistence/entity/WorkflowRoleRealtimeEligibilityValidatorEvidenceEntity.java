package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_realtime_eligibility_validator_evidence")
public class WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity extends WorkflowAuditedEntity {
    private Long evidenceId; private String validatorCode; private Integer validatorOrder;
    private String status; private String reasonCode; private String evidenceHash;
    private LocalDateTime checkedAt; private String canonicalVersion;
}
