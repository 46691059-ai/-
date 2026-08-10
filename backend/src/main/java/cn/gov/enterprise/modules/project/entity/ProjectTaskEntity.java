package cn.gov.enterprise.modules.project.entity;

import cn.gov.enterprise.common.persistence.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField("task_content")
    private String taskContent;
    private Long responsiblePerson;
    private LocalDate planDate;
    @TableField("plan_start")
    private LocalDate planStart;
    @TableField("plan_end")
    private LocalDate planEnd;
    private LocalDate actualDate;
    @TableField("actual_start")
    private LocalDate actualStart;
    @TableField("actual_end")
    private LocalDate actualEnd;
    private String status;
    private String priority;
    private BigDecimal progress;
    private Integer sortNo;
    @TableField("delete_token")
    private Long deleteToken;
}
