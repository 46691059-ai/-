package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workflow_role_canary_scope_governance")
public class CanaryGovernanceEntity extends WorkflowAuditedEntity {
    private Long previousRecordId;
    private Long enterpriseId;
    private Long organizationId;
    private Long definitionId;
    private Long definitionVersionId;
    private Long nodeId;
    private String roleCode;
    private String governanceState;
    private Long governanceRevision;
    private String directoryRevision;
    private Integer directoryCandidateCount;
    private String directoryResultHash;
    private String versionBindingHash;
    private String manifestHash;
    private String contentHash;
    private String releaseTag;
    private String releaseCommit;
    private String structuralFingerprint;
    private String approvalActor;
    private LocalDateTime approvedAt;
    private String enablementActor;
    private LocalDateTime enabledAt;
    private LocalDateTime suspendedAt;
    private LocalDateTime revokedAt;
    private String reason;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
