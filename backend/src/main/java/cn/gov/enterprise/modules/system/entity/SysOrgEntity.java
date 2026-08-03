package cn.gov.enterprise.modules.system.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 组织架构主数据实体，对应sys_org。 */
@Getter
@Setter
@TableName("sys_org")
public class SysOrgEntity extends BaseIdEntity {
    private String orgCode;
    private String orgName;
    private String orgType;
    private Long parentId;
    private Long leaderId;
    private String treePath;
    private Integer treeLevel;
    private Integer sortNo;
    private Integer status;
}
