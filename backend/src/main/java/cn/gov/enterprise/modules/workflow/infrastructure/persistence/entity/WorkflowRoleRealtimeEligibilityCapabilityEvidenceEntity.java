package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_realtime_eligibility_capability_evidence")
public class WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity extends WorkflowAuditedEntity {
    private Long evidenceId; private String capabilityCode; private String validatorCode;
    private String status; private String decision; private String providerVersion; private String policyVersion;
    private String evidenceHash; private LocalDateTime checkedAt; private LocalDateTime validUntil;
    private String canonicalVersion;
}
