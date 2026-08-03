package cn.gov.enterprise.common.datascope.vo;

/** 数据权限测试查询中的一行模拟业务数据。 */
public record DataScopeRowVO(
        Long id,
        Long orgId,
        Long ownerUserId,
        String orgName,
        String ownerUsername) {
}
