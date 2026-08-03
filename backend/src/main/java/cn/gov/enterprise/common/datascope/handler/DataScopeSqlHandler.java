package cn.gov.enterprise.common.datascope.handler;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataScopeRule;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将可信的数据权限上下文转换为不含外部输入的SQL条件。 */
@Component
public class DataScopeSqlHandler {
    private static final Pattern SAFE_COLUMN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    public String condition(DataPermissionContext context, DataScopeRule rule) {
        if (context.unrestricted()) return null;

        String orgCondition = null;
        if (context.dataScope() != DataScopeType.SELF && !context.allowedOrgIds().isEmpty()) {
            String ids = context.allowedOrgIds().stream().sorted(Comparator.naturalOrder())
                    .map(String::valueOf).collect(Collectors.joining(","));
            orgCondition = safeColumn(rule.orgField(), "组织字段") + " IN (" + ids + ")";
        }
        String selfCondition = null;
        if (context.dataScope() == DataScopeType.SELF || context.selfIncluded()) {
            selfCondition = safeColumn(rule.userField(), "用户字段") + " = " + selfValue(context, rule);
        }
        if (orgCondition != null && selfCondition != null) {
            return "(" + orgCondition + " OR " + selfCondition + ")";
        }
        if (orgCondition != null) return orgCondition;
        if (selfCondition != null) return selfCondition;
        return "1 = 0";
    }

    private String selfValue(DataPermissionContext context, DataScopeRule rule) {
        if (rule.selfValueType() == SelfValueType.USER_ID) {
            if (context.userId() == null) {
                throw new AccessDeniedException("当前用户ID缺失，数据查询已拒绝");
            }
            return String.valueOf(context.userId());
        }
        if (!StringUtils.hasText(context.username())) {
            throw new AccessDeniedException("当前用户名缺失，数据查询已拒绝");
        }
        return "'" + context.username().replace("'", "''") + "'";
    }

    private String safeColumn(String value, String label) {
        if (!StringUtils.hasText(value) || !SAFE_COLUMN.matcher(value.trim()).matches()) {
            throw new AccessDeniedException(label + "配置不安全，数据查询已拒绝");
        }
        return value.trim();
    }
}
