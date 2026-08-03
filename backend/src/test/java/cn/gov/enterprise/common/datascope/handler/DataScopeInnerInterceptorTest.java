package cn.gov.enterprise.common.datascope.handler;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataScopeInvocation;
import cn.gov.enterprise.common.datascope.context.DataScopeRule;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataScopeInnerInterceptorTest {
    private final DataScopeInnerInterceptor interceptor =
            new DataScopeInnerInterceptor(new DataScopeSqlHandler());

    @Test
    void appendsOrganizationConditionAndPreservesExistingWhere() {
        String sql = "SELECT scoped.id FROM sys_org scoped WHERE scoped.status = 1 ORDER BY scoped.id";
        String parsed = interceptor.parserSingle(sql, invocation(
                DataScopeType.ORG_AND_CHILDREN, Set.of(103L, 10301L), false));

        assertThat(parsed).contains("scoped.status = 1");
        assertThat(parsed).contains("scoped.org_id IN (103, 10301)");
    }

    @Test
    void appendsSelfCondition() {
        String parsed = interceptor.parserSingle(
                "SELECT scoped.id FROM sys_org scoped", invocation(DataScopeType.SELF, Set.of(), true));

        assertThat(parsed).contains("scoped.owner_user_id = 90002");
    }

    @Test
    void allScopeLeavesSqlUnrestricted() {
        String sql = "SELECT scoped.id FROM sys_org scoped";
        assertThat(interceptor.parserSingle(sql, invocation(DataScopeType.ALL, Set.of(), false)))
                .isEqualTo(sql);
    }

    private DataScopeInvocation invocation(DataScopeType type, Set<Long> orgIds, boolean self) {
        var context = new DataPermissionContext(90002L, "employee_test", 10301L,
                Set.of(7L), type, orgIds, self);
        return new DataScopeInvocation(context,
                new DataScopeRule("scoped.org_id", "scoped.owner_user_id", SelfValueType.USER_ID));
    }
}
