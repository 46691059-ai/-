package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.service.ResolverBindingCoveragePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolverBindingCoveragePolicyTest {
    private final ResolverBindingCoveragePolicy policy = new ResolverBindingCoveragePolicy();

    @Test
    void onlyEnabledApprovalAndCountersignNodesRequireBindings() {
        assertThat(policy.requiresResolverBinding(node(1L, WorkflowNode.NodeType.APPROVAL, true))).isTrue();
        assertThat(policy.requiresResolverBinding(node(2L, WorkflowNode.NodeType.COUNTERSIGN, true))).isTrue();
        assertThat(policy.requiresResolverBinding(node(3L, WorkflowNode.NodeType.CONDITION, true))).isFalse();
        assertThat(policy.requiresResolverBinding(node(4L, WorkflowNode.NodeType.APPROVAL, false))).isFalse();
    }

    @Test
    void completeCoverageCanHaveMultipleBindingsPerRequiredNodeAndNoConditionBinding() {
        List<WorkflowNode> nodes = List.of(
                node(1L, WorkflowNode.NodeType.APPROVAL, true),
                node(2L, WorkflowNode.NodeType.COUNTERSIGN, true),
                node(3L, WorkflowNode.NodeType.CONDITION, true));
        assertThatCode(() -> policy.validate(10L, 20L, nodes,
                List.of(binding(1L, 1, "ROLE_APPROVER"),
                        binding(1L, 2, "ROLE_REVIEWER"),
                        binding(2L, 1, "ROLE_COUNTERSIGN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void missingOrOrphanAndCrossOwnershipBindingsMustFailClosed() {
        List<WorkflowNode> nodes = List.of(
                node(1L, WorkflowNode.NodeType.APPROVAL, true),
                node(2L, WorkflowNode.NodeType.COUNTERSIGN, true),
                node(3L, WorkflowNode.NodeType.CONDITION, true));
        assertThatThrownBy(() -> policy.validate(10L, 20L, nodes,
                List.of(binding(1L, 1, "ROLE_APPROVER"))))
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> policy.validate(10L, 20L, nodes,
                List.of(binding(1L, 1, "ROLE_APPROVER"),
                        binding(2L, 1, "ROLE_COUNTERSIGN"),
                        binding(3L, 1, "ROLE_CONDITION"))))
                .hasMessageContaining("non-task");
        assertThatThrownBy(() -> policy.validate(10L, 20L, nodes,
                List.of(binding(1L, 1, "ROLE_APPROVER"),
                        binding(99L, 1, "ROLE_ORPHAN"))))
                .hasMessageContaining("orphan");
        VersionNodeResolverBinding cross = binding(2L, 1, "ROLE_COUNTERSIGN", 11L, 20L);
        assertThatThrownBy(() -> policy.validate(10L, 20L, nodes,
                List.of(binding(1L, 1, "ROLE_APPROVER"), cross)))
                .hasMessageContaining("another Definition or Version");
    }

    private WorkflowNode node(Long id, WorkflowNode.NodeType type, boolean enabled) {
        return new WorkflowNode(id, 20L, "NODE_" + id, "Node " + id, type, id.intValue(),
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                type == WorkflowNode.NodeType.COUNTERSIGN
                        ? WorkflowNode.ApprovalMode.ALL : WorkflowNode.ApprovalMode.SINGLE,
                null, WorkflowNode.AssignmentRuleType.RULE, "{}", null, null,
                null, true, enabled, 0);
    }

    private VersionNodeResolverBinding binding(Long nodeId, int order, String roleCode) {
        return binding(nodeId, order, roleCode, 10L, 20L);
    }

    private VersionNodeResolverBinding binding(
            Long nodeId, int order, String roleCode, Long definitionId, Long versionId) {
        return new VersionNodeResolverBinding(
                nodeId * 10 + order, definitionId, versionId, nodeId, order,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of("a".repeat(64)), AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE, roleCode,
                OrganizationScopeType.FIXED_ORG, 88L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "0".repeat(64), 0);
    }
}
