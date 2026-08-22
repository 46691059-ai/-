package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowVersionContentHasher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionDomainTest {
    @Test
    void definitionAndVersionFactoriesShouldCreateDraftModels() {
        WorkflowDefinition definition = WorkflowDefinition.draft(
                1L, "INVESTMENT_DECISION", "投资决策", "INVESTMENT", 10L, 20L, null);
        WorkflowVersion version = WorkflowVersion.draft(2L, 1L, 1, "1.0", "initial", null);

        assertThat(definition.status()).isEqualTo(WorkflowDefinition.Status.DRAFT);
        assertThat(version.status()).isEqualTo(WorkflowVersion.Status.DRAFT);
        assertThat(version.isEditable()).isTrue();
    }

    @Test
    void quorumNodeShouldRequireAValidThreshold() {
        assertThatThrownBy(() -> node(WorkflowNode.ApprovalMode.QUORUM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
        assertThat(node(WorkflowNode.ApprovalMode.QUORUM, 60).approvalThreshold()).isEqualTo(60);
    }

    @Test
    void versionStateMachineMustBeForwardOnly() {
        WorkflowVersion draft = WorkflowVersion.draft(2L, 1L, 1, "1.0", null, null);
        WorkflowVersion published = draft.publish("a".repeat(64), 7L, LocalDateTime.now());
        WorkflowVersion retired = published.retire(LocalDateTime.now().plusSeconds(1));
        assertThat(published.status()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
        assertThat(retired.status()).isEqualTo(WorkflowVersion.Status.RETIRED);
        assertThatThrownBy(() -> published.publish("b".repeat(64), 7L, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> retired.retire(LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semanticHashMustIgnorePersistenceIdsAndInputOrder() {
        WorkflowDefinition definition = WorkflowDefinition.draft(1L, "WF", "Workflow", "TEST", 10L, 20L, null);
        WorkflowVersion version = WorkflowVersion.draft(2L, 1L, 1, "1.0", null, null);
        WorkflowNode first = node(WorkflowNode.ApprovalMode.SINGLE, null);
        WorkflowNode sameSemanticDifferentId = first.copyTo(999L, 888L);
        WorkflowVersionContentHasher hasher = new WorkflowVersionContentHasher();
        assertThat(hasher.hash(definition, version, List.of(first)))
                .isEqualTo(hasher.hash(definition, version, List.of(sameSemanticDifferentId)));
    }

    @Test
    void domainTypesMustNotCarryFrameworkAnnotations() {
        for (Class<?> type : Arrays.asList(
                WorkflowDefinition.class, WorkflowVersion.class, WorkflowNode.class,
                WorkflowInstance.class, WorkflowTask.class, WorkflowVersionRelease.class,
                WorkflowVersionContentHasher.class)) {
            assertThat(type.getDeclaredAnnotations()).isEmpty();
            assertThat(Arrays.stream(type.getDeclaredFields()).map(field -> field.getType().getName()))
                    .noneMatch(name -> name.startsWith("org.springframework")
                            || name.startsWith("com.baomidou")
                            || name.contains("infrastructure.persistence.entity"));
        }
    }

    private WorkflowNode node(WorkflowNode.ApprovalMode mode, Integer threshold) {
        return new WorkflowNode(3L, 2L, "BOARD", "董事会", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.BOARD_DECISION, mode, threshold,
                WorkflowNode.AssignmentRuleType.ORG_POSITION, "{}", null, null,
                1440, false, true, 0);
    }
}
