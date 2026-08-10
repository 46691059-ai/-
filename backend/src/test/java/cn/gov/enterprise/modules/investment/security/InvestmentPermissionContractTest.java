package cn.gov.enterprise.modules.investment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.investment.application.security.InvestmentPermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvestmentPermissionContractTest {

    @Test
    void shouldExposeTheApprovedInvestmentPermissionSet() {
        assertThat(InvestmentPermissions.ALL).containsExactlyInAnyOrderElementsOf(Set.of(
                "investment:view",
                "investment:create",
                "investment:edit",
                "investment:approve",
                "investment:decision",
                "investment:opportunity:create",
                "investment:opportunity:review",
                "investment:opportunity:convert",
                "investment:feasibility:view",
                "investment:feasibility:edit",
                "investment:due_diligence:view",
                "investment:due_diligence:edit",
                "investment:scheme:view",
                "investment:scheme:edit",
                "investment:decision:view",
                "investment:decision:create",
                "investment:decision:submit",
                "investment:decision:withdraw",
                "investment:decision:approve",
                "investment:decision:condition",
                "investment:decision:archive"));
        assertThat(InvestmentPermissions.ALL)
                .allMatch(code -> code.matches("investment:[a-z_]+(:[a-z_]+)?"));
    }

    @Test
    void permissionSetMustBeImmutable() {
        assertThatThrownBy(() -> InvestmentPermissions.ALL.add("investment:unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
