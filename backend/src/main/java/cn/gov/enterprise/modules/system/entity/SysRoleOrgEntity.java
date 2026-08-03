package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色自定义组织数据范围关系，对应 sys_role_org。 */
@Getter
@Setter
@TableName("sys_role_org")
public class SysRoleOrgEntity extends BaseIdEntity {
    private Long roleId;
    private Long orgId;
}
