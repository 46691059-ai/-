package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_runtime_execution_admission_evidence")
public class WorkflowRoleRuntimeExecutionAdmissionEvidenceEntity extends WorkflowAuditedEntity {
    private Long admissionRowId; private String admissionId; private Integer sequenceNo;
    private String validatorCode; private String evidenceType; private String result; private String blockReason;
    private String capabilityCode; private String capabilityStatus; private String providerVersion;
    private String policyVersion; private String observedValueCode; private String scopeType;
    private String scopeEnterpriseId; private Long scopeDefinitionId; private Long scopeDefinitionVersionId;
    private Long scopeNodeId; private LocalDateTime checkedAt; private String subjectHash;
    private String evidenceHash; private String canonicalVersion;
}
