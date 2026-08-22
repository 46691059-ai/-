package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Exact mapping of the V2.5 created/updated audit-column convention. */
@Getter
@Setter
public abstract class WorkflowAuditedEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
    private Long deleteToken;
    private String remark;
    @Version
    private Integer version;
}
