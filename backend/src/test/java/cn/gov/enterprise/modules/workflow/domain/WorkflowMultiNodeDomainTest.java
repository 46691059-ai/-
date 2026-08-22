package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WorkflowMultiNodeDomainTest {
    @Test
    void directTransitionMustRejectConditionAndSelfLoop() {
        assertThatThrownBy(() -> transition(1L, 2L, WorkflowTransition.RouteType.DIRECT, "{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conditionConfig");
        assertThatThrownBy(() -> transition(1L, 1L, WorkflowTransition.RouteType.DIRECT, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source node");
    }

    @Test
    void nodeExecutionMustFollowFrozenStateMachine() {
        LocalDateTime entered = LocalDateTime.now();
        WorkflowNodeExecution created = WorkflowNodeExecution.created(
                20L, "WFNE-20", instance(), node(), 1, null, null, "trace", entered);
        WorkflowNodeExecution active = created.activate(entered.plusSeconds(1));
        WorkflowNodeExecution completed = active.complete(9L, "APPROVED", entered.plusSeconds(2));

        assertThat(active.status()).isEqualTo(WorkflowNodeExecution.Status.ACTIVE);
        assertThat(completed.status()).isEqualTo(WorkflowNodeExecution.Status.COMPLETED);
        assertThat(completed.completedBy()).isEqualTo(9L);
        assertThat(completed.version()).isEqualTo(2);
        assertThatThrownBy(() -> completed.reject(9L, "REJECTED", entered.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedExecutionMustCarryFailureCode() {
        WorkflowNodeExecution created = WorkflowNodeExecution.created(
                20L, "WFNE-20", instance(), node(), 1, null, null, null, LocalDateTime.now());
        assertThatThrownBy(() -> created.fail(" ", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(created.fail("ASSIGNMENT_EMPTY", LocalDateTime.now()).failureCode())
                .isEqualTo("ASSIGNMENT_EMPTY");
    }

    private WorkflowTransition transition(
            Long from, Long to, WorkflowTransition.RouteType routeType, String condition) {
        return new WorkflowTransition(1L, 2L, "T-1", "Next", from, to,
                WorkflowTransition.TriggerType.APPROVE, routeType, 1, condition, true, 0);
    }

    private WorkflowInstance instance() {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "CONTRACT", "Contract",
                "CONTRACT", 100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "1.0", "hash", null, now, null, 9L, now, null, 0);
        return WorkflowInstance.running(10L, "WFI-10", definition, version, "CONTRACT", "1",
                "CONTRACT:1", 100L, null, null, 1, 9L, 30L, 3L,
                null, "idem", "request", null, now);
    }

    private WorkflowNode node() {
        return new WorkflowNode(3L, 2L, "REVIEW", "Review", WorkflowNode.NodeType.APPROVAL,
                1, WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null, WorkflowNode.AssignmentRuleType.USER,
                "{}", null, null, null, false, true, 0);
    }
}
