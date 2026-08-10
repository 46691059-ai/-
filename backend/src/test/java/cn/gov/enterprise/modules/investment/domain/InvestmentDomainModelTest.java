package cn.gov.enterprise.modules.investment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecision;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentDomainModelTest {
    @Test
    void shouldCreateFrameworkIndependentInvestmentModels() {
        InvestmentProject project = new InvestmentProject(
                null, 100L, "INV-001", "储能投资", InvestmentProject.InvestmentType.EQUITY,
                InvestmentProject.InvestmentMethod.CASH, new BigDecimal("1000"),
                new BigDecimal("51"), 10L, 20L, InvestmentProject.Status.DRAFT);
        InvestmentOpportunity opportunity = new InvestmentOpportunity(
                null, "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                10L, 20L, "新能源", null, BigDecimal.ZERO,
                InvestmentOpportunity.Status.EVALUATING, "评估中", null, null, null, 0);
        InvestmentFeasibility feasibility = new InvestmentFeasibility(
                null, 200L, null, null, InvestmentFeasibility.Status.NOT_STARTED, 0);
        InvestmentDecision decision = new InvestmentDecision(
                null, 200L, InvestmentDecision.DecisionType.BOARD,
                LocalDate.of(2026, 8, 3), 20L, InvestmentDecision.Result.APPROVED,
                "MAJOR-2026-001", null, InvestmentDecision.Status.EFFECTIVE);
        assertThat(project.projectId()).isEqualTo(100L);
        assertThat(opportunity.status()).isEqualTo(InvestmentOpportunity.Status.EVALUATING);
        assertThat(feasibility.investmentProjectId()).isEqualTo(200L);
        assertThat(decision.result()).isEqualTo(InvestmentDecision.Result.APPROVED);
    }

    @Test
    void shouldEnforceCoreBootstrapInvariants() {
        assertThatThrownBy(() -> new InvestmentProject(
                null, 100L, "INV-002", "Invalid ratio",
                InvestmentProject.InvestmentType.EQUITY, InvestmentProject.InvestmentMethod.CASH,
                BigDecimal.ONE, new BigDecimal("101"), 10L, 20L,
                InvestmentProject.Status.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void domainTypesMustNotDependOnFrameworkPackages() {
        List<Class<?>> domainTypes = List.of(
                InvestmentProject.class, InvestmentOpportunity.class,
                InvestmentFeasibility.class, InvestmentDecision.class);
        assertThat(domainTypes).allSatisfy(type -> {
            assertThat(type.getAnnotations()).isEmpty();
            assertThat(List.of(type.getDeclaredFields())).allSatisfy(field ->
                    assertThat(field.getType().getPackageName())
                            .doesNotStartWith("org.springframework")
                            .doesNotStartWith("com.baomidou")
                            .doesNotContain(".entity"));
        });
    }
}
