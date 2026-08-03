package cn.gov.enterprise.common.datascope.context;

/** 注解解析后的 SQL 字段与本人标识规则。 */
public record DataScopeRule(
        String orgField,
        String userField,
        SelfValueType selfValueType) {
}
