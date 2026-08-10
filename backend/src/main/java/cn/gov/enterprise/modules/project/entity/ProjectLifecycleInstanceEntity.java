package cn.gov.enterprise.modules.project.entity;

import cn.gov.enterprise.common.persistence.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("project_lifecycle_instance")
public class ProjectLifecycleInstanceEntity extends BaseEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long projectId;
    private String sourceType;
    private Long templateId;
    private Long templateVersionId;
    private String templateCodeSnapshot;
    private String templateNameSnapshot;
    private Integer versionNoSnapshot;
    private String versionNameSnapshot;
    private String templateChecksum;
    private String snapshotChecksum;
    private String progressPolicySnapshot;
    private String snapshotStatus;
    private String status;
    private String initializedBy;
    private LocalDateTime initializedTime;
    private Long deleteToken;
}
