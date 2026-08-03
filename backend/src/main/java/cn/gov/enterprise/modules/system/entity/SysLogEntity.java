package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统操作审计日志实体，对应sys_log。 */
@Getter
@Setter
@TableName("sys_log")
public class SysLogEntity extends BaseIdEntity {
    private Long userId;
    private String operation;
    private String requestUrl;
    private String requestMethod;
    private String ip;
    private String result;
    private String errorMessage;
    private Long durationMs;
    private String traceId;
}
