package cn.gov.enterprise.modules.system.org.vo;

import java.time.LocalDateTime;

public record OrgVO(
        Long id,
        String orgCode,
        String orgName,
        String orgType,
        Long parentId,
        String parentName,
        Long leaderId,
        Long leaderUserId,
        String leaderName,
        String treePath,
        Integer treeLevel,
        Integer sortNo,
        Integer status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        Integer version) {
}
