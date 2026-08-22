package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.service.NodeExecutionService;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowLinearExecutor;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowLinearGraphValidator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowLinearEngineDomainTest {
    @Test
    void aToBToCGraphMustHaveAAsItsOnlyEntry() {
        var validator = new WorkflowLinearGraphValidator();
        WorkflowNode entry = validator.validateAndFindEntry(2L, nodes(), transitions());
        assertThat(entry.nodeCode()).isEqualTo("A");
    }

    @Test
    void completingNodeWithNoOutgoingTransitionMustProduceTerminalDecision() {
        Fixture fixture = fixture(node(3L, "A", 1));
        var result = new WorkflowLinearExecutor().complete(fixture.task, fixture.execution,
                20L, LocalDateTime.now(), List.of());
        assertThat(result.task().status()).isEqualTo(WorkflowTask.Status.APPROVED);
        assertThat(result.execution().status()).isEqualTo(WorkflowNodeExecution.Status.COMPLETED);
        assertThat(result.transition()).isEmpty();
    }

    @Test
    void multipleApproveTransitionsMustBeRejected() {
        Fixture fixture = fixture(node(3L, "A", 1));
        WorkflowTransition first = transition(10L, 3L, 4L, "A_B");
        WorkflowTransition second = transition(11L, 3L, 5L, "A_C");
        assertThatThrownBy(() -> new WorkflowLinearExecutor().complete(
                fixture.task, fixture.execution, 20L, LocalDateTime.now(), List.of(first, second)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("multiple");
    }

    @Test
    void crossVersionTransitionMustBeRejected() {
        Fixture fixture = fixture(node(3L, "A", 1));
        WorkflowTransition crossVersion = new WorkflowTransition(10L, 99L, "A_B", "A到B",
                3L, 4L, WorkflowTransition.TriggerType.APPROVE,
                WorkflowTransition.RouteType.DIRECT, 1, null, true, 0);
        assertThatThrownBy(() -> new WorkflowLinearExecutor().complete(
                fixture.task, fixture.execution, 20L, LocalDateTime.now(), List.of(crossVersion)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid");
    }

    private Fixture fixture(WorkflowNode node) {
        WorkflowInstance instance = instance(node.id());
        WorkflowNodeExecution execution = new NodeExecutionService().enter(7L, "WFNE-7",
                instance, node, 1, null, null, null, LocalDateTime.now());
        WorkflowTask task = WorkflowTask.pendingLinear(8L, "WFT-8", instance, node,
                execution, 20L, node.assignmentRuleConfig(), null);
        return new Fixture(task, execution);
    }

    private WorkflowInstance instance(Long nodeId) {
        LocalDateTime now = LocalDateTime.now();
        WorkflowDefinition definition = new WorkflowDefinition(1L, "LINEAR", "线性流程", "TEST",
                100L, 30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 0);
        WorkflowVersion version = new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "2.0", "a".repeat(64), null, now, null, 20L, now, null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256, 0);
        return WorkflowInstance.runningLinear(6L, "WFI-6", definition, version, "TEST", "1",
                "TEST:1", 100L, null, null, 1, 20L, 30L, nodeId, null,
                "start", "b".repeat(64), null, now);
    }

    private List<WorkflowNode> nodes() {
        return List.of(node(3L, "A", 1), node(4L, "B", 2), node(5L, "C", 3));
    }

    private List<WorkflowTransition> transitions() {
        return List.of(transition(10L, 3L, 4L, "A_B"), transition(11L, 4L, 5L, "B_C"));
    }

    private WorkflowNode node(Long id, String code, int order) {
        return new WorkflowNode(id, 2L, code, code, WorkflowNode.NodeType.APPROVAL, order,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null, WorkflowNode.AssignmentRuleType.USER,
                "{\"userId\":20}", null, null, null, true, true, 0);
    }

    private WorkflowTransition transition(Long id, Long from, Long to, String code) {
        return new WorkflowTransition(id, 2L, code, code, from, to,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0);
    }

    private record Fixture(WorkflowTask task, WorkflowNodeExecution execution) {}
}
