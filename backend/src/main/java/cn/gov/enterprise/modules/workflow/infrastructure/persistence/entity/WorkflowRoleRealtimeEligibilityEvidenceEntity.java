package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("workflow_role_realtime_eligibility_evidence")
public class WorkflowRoleRealtimeEligibilityEvidenceEntity extends WorkflowAuditedEntity {
    private String eligibilityEvidenceId; private String eligibilityRequestId; private String claimRequestId;
    private String claimIdempotencyKey; private Integer attemptNo; private String correlationId;
    private Long instanceId; private Long definitionVersionId; private Long nodeId; private Long nodeExecutionId;
    private Long taskId; private Long candidatePoolId; private Long candidateMemberId; private Long candidateUserId;
    private Long bindingSetId; private Long resolverBindingId; private Long nodeResolverBindingId;
    private String roleCode; private String organizationId; private String candidatePoolHash;
    private String runtimeBindingHash; private String eligibilityHash; private String candidateDirectoryRevision;
    private String claimDirectoryRevision; private String directoryResultHash; private String directoryContractHash;
    private Integer directoryComplete; private LocalDateTime directoryEffectiveAt; private LocalDateTime directoryCheckedAt;
    private String decision; private Integer terminalValidatorOrder; private Integer validatorCount; private Integer capabilityCount;
    private String validatorEvidenceRootHash; private String capabilityEvidenceRootHash; private String persistenceHash;
    private String eligibilityCanonicalVersion; private String persistenceCanonicalVersion; private String policyVersion;
    private String evidenceSource; private LocalDateTime claimAt; private LocalDateTime verifiedAt; private LocalDateTime expiresAt;
}
