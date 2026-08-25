package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_version")
public class WorkflowVersionEntity extends WorkflowAuditedEntity {
    private Long definitionId;
    private Integer versionNo;
    private String status;
    private String schemaVersion;
    private String engineMode;
    private String contentHashAlgorithm;
    private String resolverBindingModel;
    private String resolverBindingManifestHash;
    private Integer resolverBindingCount;
    private String resolverBindingCanonicalVersion;
    private String contentHash;
    private String changeNote;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long publishedBy;
    private LocalDateTime publishedTime;
    private Long sourceVersionId;
}
