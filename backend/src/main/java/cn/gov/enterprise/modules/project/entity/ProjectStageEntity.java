package cn.gov.enterprise.modules.project.entity;

import cn.gov.enterprise.common.persistence.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("project_stage")
public class ProjectStageEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String stageCode;
    private String stageName;
    private Integer stageOrder;
    private LocalDate startTime;
    private LocalDate endTime;
    private LocalDate actualStartTime;
    private LocalDate actualEndTime;
    private String status;
    private Long responsiblePerson;
    private String approvalStatus;
    private BigDecimal completionPercent;
}
