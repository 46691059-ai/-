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
@TableName("project_task")
public class ProjectTaskEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long stageId;
    private Long parentTaskId;
    private String taskNo;
    private String taskName;
    private Long responsiblePerson;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String status;
    private String priority;
    private BigDecimal progress;
    private Integer sortNo;
}
