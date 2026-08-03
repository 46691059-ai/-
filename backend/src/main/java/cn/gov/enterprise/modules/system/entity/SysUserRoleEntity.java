package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 用户角色关系实体，对应sys_user_role。 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRoleEntity extends BaseIdEntity {
    private Long userId;
    private Long roleId;
}
