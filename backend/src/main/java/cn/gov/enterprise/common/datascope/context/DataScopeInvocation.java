package cn.gov.enterprise.common.datascope.context;

/** ThreadLocal栈中的单次数据权限调用帧。 */
public record DataScopeInvocation(DataPermissionContext context, DataScopeRule rule) {
}
