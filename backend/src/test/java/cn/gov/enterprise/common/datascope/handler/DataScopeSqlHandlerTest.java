package cn.gov.enterprise.common.datascope.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataScopeRule;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.context.SelfValueType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DataScopeSqlHandlerTest {
    private final DataScopeSqlHandler handler = new DataScopeSqlHandler();
    private final DataScopeRule rule = new DataScopeRule("biz.org_id", "biz.owner_user_id", SelfValueType.USER_ID);

    @Test
    void allScopeDoesNotAppendCondition() {
        assertThat(handler.condition(context(DataScopeType.ALL, Set.of(), false), rule)).isNull();
    }

    @Test
    void organizationAndChildrenUsesOnlyTrustedNumericIds() {
        String condition = handler.condition(
                context(DataScopeType.ORG_AND_CHILDREN, Set.of(10303L, 103L, 10302L), false), rule);

        assertThat(condition).isEqualTo("biz.org_id IN (103,10302,10303)");
    }

    @Test
    void selfScopeFiltersByCurrentUser() {
        assertThat(handler.condition(context(DataScopeType.SELF, Set.of(), true), rule))
                .isEqualTo("biz.owner_user_id = 90002");
    }

    @Test
    void usernameAuditFieldUsesEscapedAuthenticatedUsername() {
        var usernameContext = new DataPermissionContext(9L, "o'reilly", 10301L,
                Set.of(7L), DataScopeType.SELF, Set.of(), true);
        var usernameRule = new DataScopeRule("biz.org_id", "biz.create_by", SelfValueType.USERNAME);

        assertThat(handler.condition(usernameContext, usernameRule))
                .isEqualTo("biz.create_by = 'o''reilly'");
    }

    @Test
    void emptyCustomScopeFailsClosed() {
        assertThat(handler.condition(context(DataScopeType.CUSTOM, Set.of(), false), rule))
                .isEqualTo("1 = 0");
    }

    @Test
    void unsafeColumnConfigurationIsRejected() {
        assertThatThrownBy(() -> handler.condition(
                context(DataScopeType.ORG, Set.of(103L), false),
                new DataScopeRule("org_id) OR 1=1 --", "create_by", SelfValueType.USERNAME)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private DataPermissionContext context(DataScopeType scope, Set<Long> orgIds, boolean self) {
        return new DataPermissionContext(90002L, "employee_test", 10301L,
                Set.of(7L), scope, orgIds, self);
    }
}
