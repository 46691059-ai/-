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
@TableName("project_info")
public class ProjectEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String projectNo;
    private String projectName;
    private String projectType;
    private String projectMode;
    @TableField("source_type")
    private String sourceType;
    @TableField("customer_id")
    private Long customerId;
    private Long leaderId;
    private Long departmentId;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private BigDecimal budgetAmount;
    @TableField("contract_amount")
    private BigDecimal contractAmount;
    private BigDecimal expectedIncome;
    private BigDecimal expectedProfit;
    @TableField("actual_income")
    private BigDecimal actualIncome;
    @TableField("actual_profit")
    private BigDecimal actualProfit;
    private String currentStageCode;
    private String riskLevel;
    private BigDecimal progress;
    private String description;
    @TableField("delete_token")
    private Long deleteToken;
}
