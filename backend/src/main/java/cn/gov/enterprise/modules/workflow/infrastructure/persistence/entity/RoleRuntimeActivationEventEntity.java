package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("workflow_role_runtime_activation_event")
public class RoleRuntimeActivationEventEntity extends WorkflowAuditedEntity {
    private String eventId;
    private Long enterpriseId;
    private Long organizationId;
    private Long definitionId;
    private Long definitionVersionId;
    private Long nodeId;
    private String roleCode;
    private String eventType;
    private Long sequence;
    private Long revision;
    private String previousState;
    private String resultingState;
    private String authorizationId;
    private String authorizationType;
    private String authorizationCommit;
    private String observationEvidenceCommit;
    private String runtimeEnablementEvidenceCommit;
    private String runtimeReleaseCommit;
    private String runtimeReleaseTag;
    private String directoryResultHash;
    private String versionBindingHash;
    private String manifestHash;
    private String contentHash;
    private String structuralFingerprint;
    private String actorType;
    private String actorId;
    private LocalDateTime occurredAt;
}
