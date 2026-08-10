package cn.gov.enterprise.modules.investment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InvestmentOpportunityStateMachineTest {

    @Test
    void shouldCompleteRegisterScreenAnalyzeEvaluateAndConvertFlow() {
        InvestmentOpportunity registered = opportunity(InvestmentOpportunity.Status.REGISTERED);

        InvestmentOpportunity screening = registered.submit();
        InvestmentOpportunity analysing = screening.approveReview("初筛通过");
        InvestmentOpportunity evaluating = analysing.approveReview("分析通过");
        InvestmentOpportunity converted = evaluating.convert(10L, 100L, LocalDateTime.now());

        assertThat(screening.status()).isEqualTo(InvestmentOpportunity.Status.SCREENING);
        assertThat(analysing.status()).isEqualTo(InvestmentOpportunity.Status.ANALYSING);
        assertThat(evaluating.status()).isEqualTo(InvestmentOpportunity.Status.EVALUATING);
        assertThat(converted.status()).isEqualTo(InvestmentOpportunity.Status.CONVERTED);
        assertThat(converted.version()).isEqualTo(4);
    }

    @Test
    void rejectedOpportunityShouldBeResubmittedFromScreening() {
        InvestmentOpportunity rejected = opportunity(InvestmentOpportunity.Status.SCREENING)
                .reject("材料不完整");

        InvestmentOpportunity resubmitted = rejected.resubmit();

        assertThat(rejected.status()).isEqualTo(InvestmentOpportunity.Status.REJECTED);
        assertThat(resubmitted.status()).isEqualTo(InvestmentOpportunity.Status.SCREENING);
        assertThat(resubmitted.reviewConclusion()).isNull();
    }

    @Test
    void invalidTransitionShouldBeRejectedByAggregate() {
        assertThatThrownBy(() -> opportunity(InvestmentOpportunity.Status.REGISTERED)
                .convert(10L, 100L, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGISTERED");
        assertThatThrownBy(() -> opportunity(InvestmentOpportunity.Status.CLOSED).submit())
                .isInstanceOf(IllegalStateException.class);
    }

    private InvestmentOpportunity opportunity(InvestmentOpportunity.Status status) {
        return new InvestmentOpportunity(
                200L, "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, 40L, "新能源", null, BigDecimal.ZERO, status,
                null, null, null, null, 0);
    }
}
