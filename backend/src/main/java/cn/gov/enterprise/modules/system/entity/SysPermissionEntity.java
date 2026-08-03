package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统权限资源实体，对应sys_permission。 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermissionEntity extends BaseIdEntity {
    private String permissionName;
    private String permissionCode;
    private String permissionType;
    private String resourcePath;
    private String httpMethod;
    private String moduleCode;
    private Integer status;
}
