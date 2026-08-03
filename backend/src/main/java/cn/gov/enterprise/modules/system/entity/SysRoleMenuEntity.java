package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色菜单关系实体，对应sys_role_menu。 */
@Getter
@Setter
@TableName("sys_role_menu")
public class SysRoleMenuEntity extends BaseIdEntity {
    private Long roleId;
    private Long menuId;
}
