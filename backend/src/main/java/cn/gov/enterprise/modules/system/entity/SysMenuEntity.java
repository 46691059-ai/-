package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统菜单实体，对应sys_menu。 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenuEntity extends BaseIdEntity {
    private String menuName;
    private Long parentId;
    private String menuType;
    private String path;
    private String component;
    private String permission;
    private String icon;
    private Integer sortNo;
    private Integer visible;
    private Integer status;
}
