package cn.gov.enterprise.modules.system.org.vo;

import java.util.List;

public record OrgTreeVO(
        Long id,
        String orgCode,
        String orgName,
        String orgType,
        Long parentId,
        Long leaderId,
        String leaderName,
        Integer status,
        Integer treeLevel,
        Integer sortNo,
        List<OrgTreeVO> children) {
}
