package cn.gov.enterprise.common.datascope.vo;

import cn.gov.enterprise.common.datascope.context.DataScopeType;
import java.util.List;
import java.util.Set;

/** 数据权限测试结果及当前权限快照。 */
public record DataScopeTestVO(
        Long userId,
        Long orgId,
        Set<Long> roleIds,
        DataScopeType dataScope,
        Set<Long> allowedOrgIds,
        List<DataScopeRowVO> rows) {
}
