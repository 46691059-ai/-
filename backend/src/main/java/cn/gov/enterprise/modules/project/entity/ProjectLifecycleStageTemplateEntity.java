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
@TableName("project_lifecycle_stage_template")
public class ProjectLifecycleStageTemplateEntity extends BaseEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long templateVersionId;
    private String stageCode;
    private String stageName;
    private Integer stageOrder;
    private Integer requiredFlag;
    private Integer allowSkip;
    private BigDecimal progressWeight;
    private Integer plannedDurationDays;
    private Integer autoStart;
    private Integer approvalRequired;
    private String approvalSceneCode;
    private String completionMode;
    private String description;
    private Long deleteToken;
}
