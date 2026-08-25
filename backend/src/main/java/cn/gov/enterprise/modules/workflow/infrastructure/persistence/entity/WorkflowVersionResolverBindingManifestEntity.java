package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_version_resolver_binding_manifest")
public class WorkflowVersionResolverBindingManifestEntity extends WorkflowAuditedEntity {
    private Long definitionId;
    private Long definitionVersionId;
    private String canonicalVersion;
    private Integer bindingCount;
    private String manifestHash;
    private Long releasedBy;
    private LocalDateTime releasedTime;
}
