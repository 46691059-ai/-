package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统角色实体，对应sys_role。 */
@Getter
@Setter
@TableName("sys_role")
public class SysRoleEntity extends BaseIdEntity {
    private String roleName;
    private String roleCode;
    private String description;
    private String dataScopeType;
    private Integer status;
}
