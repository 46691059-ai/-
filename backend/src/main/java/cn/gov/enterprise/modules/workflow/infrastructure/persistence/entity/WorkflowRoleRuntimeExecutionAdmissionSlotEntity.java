package cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("workflow_role_runtime_execution_admission_slot")
public class WorkflowRoleRuntimeExecutionAdmissionSlotEntity {
    @TableId private Long candidateSnapshotRowId;
    private String snapshotId; private Long sourceDeleteToken; private Long activeAdmissionRowId;
    private String activeAdmissionId; private String activeToken; private String slotStatus; @Version private Integer version;
    private String updatedBy; private LocalDateTime updatedTime;
}
