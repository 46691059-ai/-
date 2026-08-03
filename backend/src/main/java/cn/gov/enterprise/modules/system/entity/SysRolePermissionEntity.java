package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色权限资源关系实体，对应sys_role_permission。 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermissionEntity extends BaseIdEntity {
    private Long roleId;
    private Long permissionId;
}
