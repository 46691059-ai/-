package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeDomainTest {
    @Test
    void startFactoryShouldFreezeDefinitionAndVersionSnapshots() {
        WorkflowDefinition definition = activeDefinition();
        WorkflowVersion workflowVersion = publishedVersion();
        LocalDateTime now = LocalDateTime.now();

        WorkflowInstance instance = WorkflowInstance.running(10L, "WFI-10", definition, workflowVersion,
                "CONTRACT", "B-1", "CONTRACT:B-1", 100L, "SNAP-1", "hash", 1,
                20L, 30L, 3L, "{}", "idem-1", "request-hash", "trace", now);

        assertThat(instance.status()).isEqualTo(WorkflowInstance.Status.RUNNING);
        assertThat(instance.definitionCodeSnapshot()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(instance.definitionVersionNo()).isEqualTo(1);
        assertThat(instance.startedTime()).isEqualTo(now);
    }

    @Test
    void pendingTaskShouldSnapshotTheMaterializedNode() {
        WorkflowInstance instance = instance();
        WorkflowTask task = WorkflowTask.pending(11L, "WFT-11", instance, node(),
                "FIRST:1", "{\"users\":[20]}", null);

        assertThat(task.status()).isEqualTo(WorkflowTask.Status.PENDING);
        assertThat(task.nodeCodeSnapshot()).isEqualTo("FIRST");
        assertThat(task.allowedActions()).contains("APPROVE", "REJECT");
    }

    @Test
    void claimedTaskMustCarryAssigneeAndClaimTime() {
        assertThatThrownBy(() -> new WorkflowTask(11L, "WFT-11", 10L, 2L, 3L,
                "FIRST", "首节点", 1, "FIRST:1", null, "{}",
                WorkflowTask.Status.CLAIMED, "APPROVE", null, null,
                null, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalTaskTransitionsShouldBeExplicitAndNonRepeatable() {
        WorkflowTask pending = WorkflowTask.pending(11L, "WFT-11", instance(), node(),
                "FIRST:1", "{}", null);

        WorkflowTask approved = pending.approve(20L, LocalDateTime.now());
        assertThat(approved.status()).isEqualTo(WorkflowTask.Status.APPROVED);
        assertThat(approved.decisionResult()).isEqualTo("APPROVED");
        assertThatThrownBy(() -> approved.reject(20L, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pending.reject(20L, LocalDateTime.now()).status())
                .isEqualTo(WorkflowTask.Status.REJECTED);
        assertThat(pending.withdraw(20L, LocalDateTime.now()).status())
                .isEqualTo(WorkflowTask.Status.CANCELLED);
    }

    @Test
    void instanceTransitionsShouldFreezeTerminalResult() {
        WorkflowInstance running = instance();
        assertThat(running.approve(LocalDateTime.now()).status())
                .isEqualTo(WorkflowInstance.Status.APPROVED);
        assertThat(running.reject(LocalDateTime.now()).result()).isEqualTo("REJECTED");
        assertThat(running.withdraw(LocalDateTime.now()).withdrawnTime()).isNotNull();
        assertThatThrownBy(() -> running.approve(LocalDateTime.now()).reject(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private WorkflowDefinition activeDefinition() {
        return new WorkflowDefinition(1L, "CONTRACT_APPROVAL", "合同审批", "CONTRACT", 100L,
                30L, WorkflowDefinition.Status.ACTIVE, 2L, null, 1);
    }
    private WorkflowVersion publishedVersion() {
        LocalDateTime now = LocalDateTime.now();
        return new WorkflowVersion(2L, 1L, 1, WorkflowVersion.Status.PUBLISHED, "1.0",
                "content-hash", null, now, null, 20L, now, null, 1);
    }
    private WorkflowNode node() {
        return new WorkflowNode(3L, 2L, "FIRST", "首节点", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE,
                null, WorkflowNode.AssignmentRuleType.USER, "{}", null, null,
                null, false, true, 0);
    }
    private WorkflowInstance instance() {
        return WorkflowInstance.running(10L, "WFI-10", activeDefinition(), publishedVersion(),
                "CONTRACT", "B-1", "CONTRACT:B-1", 100L, null, null, 1,
                20L, 30L, 3L, null, "idem-1", "request-hash", null, LocalDateTime.now());
    }
}
