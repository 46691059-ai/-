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
    /** 跨环境稳定的菜单业务编码，不随名称、路由或父节点调整而变化。 */
    private String menuCode;
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
