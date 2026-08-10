package cn.gov.enterprise.modules.investment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import cn.gov.enterprise.modules.investment.domain.model.DecisionWorkflowEventPolicy;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvestmentDecisionApprovalStateMachineTest {
    @Test
    void decisionMustFollowCanonicalApprovalStateMachine() {
        InvestmentDecisionCase draft = decision(InvestmentDecisionCase.Status.DRAFT);
        InvestmentDecisionCase submitted = draft.transitionTo(InvestmentDecisionCase.Status.SUBMITTED);
        InvestmentDecisionCase approving = submitted.transitionTo(InvestmentDecisionCase.Status.IN_APPROVAL);
        InvestmentDecisionCase approved = approving.transitionTo(InvestmentDecisionCase.Status.APPROVED);
        assertThat(approved.transitionTo(InvestmentDecisionCase.Status.ARCHIVED).status())
                .isEqualTo(InvestmentDecisionCase.Status.ARCHIVED);
        assertThatThrownBy(() -> draft.transitionTo(InvestmentDecisionCase.Status.APPROVED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void workflowPolicyMustCoverApprovedRejectedWithdrawnAndExceptionEvents() {
        DecisionWorkflowEventPolicy policy = new DecisionWorkflowEventPolicy();
        assertThat(policy.evaluate("APPROVAL_APPROVED", null).decisionStatus())
                .isEqualTo(InvestmentDecisionCase.Status.APPROVED);
        assertThat(policy.evaluate("APPROVAL_REJECTED", null).decisionStatus())
                .isEqualTo(InvestmentDecisionCase.Status.REJECTED);
        assertThat(policy.evaluate("PROCESS_WITHDRAWN", null).decisionStatus())
                .isEqualTo(InvestmentDecisionCase.Status.WITHDRAWN);
        assertThat(policy.evaluate("PROCESS_EXCEPTION", "TIMEOUT").exceptional()).isTrue();
    }

    @Test
    void conditionMustCompleteRectificationReviewAndCloseSequence() {
        DecisionCondition open = condition(DecisionCondition.Status.OPEN);
        DecisionCondition inProgress = open.transitionTo(DecisionCondition.Status.IN_PROGRESS);
        DecisionCondition submitted = inProgress.transitionTo(DecisionCondition.Status.SUBMITTED);
        DecisionCondition verified = submitted.transitionTo(DecisionCondition.Status.VERIFIED);
        assertThat(verified.transitionTo(DecisionCondition.Status.CLOSED).status())
                .isEqualTo(DecisionCondition.Status.CLOSED);
        assertThatThrownBy(() -> open.transitionTo(DecisionCondition.Status.CLOSED))
                .isInstanceOf(IllegalStateException.class);
    }

    private static InvestmentDecisionCase decision(InvestmentDecisionCase.Status status) {
        return new InvestmentDecisionCase(1L, 2L, "D-1", "重大投资", status, 3L, 0);
    }

    private static DecisionCondition condition(DecisionCondition.Status status) {
        return new DecisionCondition(1L, 2L, 3L, 4L, "C-1", "完成风险整改", true,
                5L, 6L, LocalDate.now().plusDays(10), "HIGH", status, 0);
    }
}
