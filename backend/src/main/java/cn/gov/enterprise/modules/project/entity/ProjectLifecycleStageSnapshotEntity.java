package cn.gov.enterprise.modules.project.entity;

import cn.gov.enterprise.common.persistence.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("project_lifecycle_stage_snapshot")
public class ProjectLifecycleStageSnapshotEntity extends BaseEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long lifecycleInstanceId;
    private Long projectId;
    private Long sourceStageTemplateId;
    private String stageCode;
    private String stageName;
    private Integer stageOrder;
    private BigDecimal progressWeight;
    private String weightSource;
    private Integer requiredFlag;
    private Integer allowSkip;
    private Integer approvalRequired;
    private String approvalSceneCode;
    private String completionMode;
    private Integer plannedDurationDays;
    private String conditionSnapshotStatus;
    private String definitionChecksum;
    private Long deleteToken;
}
