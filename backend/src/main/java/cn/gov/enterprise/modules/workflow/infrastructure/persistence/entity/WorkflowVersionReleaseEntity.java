package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_version_release")
public class WorkflowVersionReleaseEntity extends WorkflowAuditedEntity {
    private Long definitionId;
    private Long previousVersionId;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private String contentHash;
    private String engineMode;
    private String contentHashAlgorithm;
    private Long operatorUserId;
    private Long operatorOrgId;
    private LocalDateTime publishedTime;
    private String traceId;
    private String validationSummary;
}
