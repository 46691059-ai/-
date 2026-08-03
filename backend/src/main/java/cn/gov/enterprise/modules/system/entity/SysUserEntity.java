package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 系统登录账号实体，对应sys_user。 */
@Getter
@Setter
@TableName("sys_user")
public class SysUserEntity extends BaseIdEntity {
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private Long orgId;
    private Long employeeId;
    private Integer status;
    private Integer tokenVersion;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
}
