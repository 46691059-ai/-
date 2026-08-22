package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.*;

import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowResolverBindingHasher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowMultiResolverBindingDomainTest {
    private final WorkflowResolverBindingHasher hasher = new WorkflowResolverBindingHasher();

    @Test
    void manifestHashIsStableAndChangesWithNodeRule() {
        WorkflowResolverBinding resolver = resolver(10L, "EXPLICIT_USER", "EXPLICIT_USER_V1",
                ExplicitUserResolver.CONTRACT_HASH.value());
        NodeResolverBinding a = node(101L, 1L, "A", "{\"userId\":7}", resolver);
        NodeResolverBinding b = node(102L, 2L, "B", "{\"userId\":8}", resolver);

        assertThat(hasher.manifestHash(List.of(a, b)))
                .isEqualTo(hasher.manifestHash(List.of(b, a)));
        NodeResolverBinding changed = node(103L, 2L, "B", "{\"userId\":9}", resolver);
        assertThat(hasher.manifestHash(List.of(a, changed)))
                .isNotEqualTo(hasher.manifestHash(List.of(a, b)));
    }

    @Test
    void oneInstanceCanFreezeNodeBindingsAgainstDifferentResolverContracts() {
        WorkflowResolverBinding first = resolver(10L, "RESOLVER_A", "A_V1", hash("A"));
        WorkflowResolverBinding second = resolver(11L, "RESOLVER_B", "B_V1", hash("B"));
        NodeResolverBinding nodeA = node(101L, 1L, "A", "{\"userId\":7}", first);
        NodeResolverBinding nodeB = node(102L, 2L, "B", "{\"userId\":8}", second);

        assertThat(nodeA.resolverBindingId()).isNotEqualTo(nodeB.resolverBindingId());
        assertThat(hasher.manifestHash(List.of(nodeA, nodeB))).matches("[0-9a-f]{64}");
    }

    @Test
    void explicitUserTargetIsReadFromFrozenSnapshot() {
        WorkflowResolverBinding resolver = resolver(10L, "EXPLICIT_USER", "EXPLICIT_USER_V1",
                ExplicitUserResolver.CONTRACT_HASH.value());
        assertThat(node(101L, 1L, "A", "{\"userId\":77}", resolver)
                .requireExplicitUserId()).isEqualTo(77L);
    }

    private WorkflowResolverBinding resolver(Long id, String code, String version, String contract) {
        return new WorkflowResolverBinding(id, 5L, 3L, 4L, ResolverCode.of(code), ResolverVersion.of(version),
                AssignmentStrategy.Type.USER, ResolverMode.DIRECT, ResolverContractHash.of(contract),
                hash(code + version), WorkflowResolverBindingSet.Status.FROZEN,
                LocalDateTime.of(2026, 8, 12, 9, 0), "test", 0);
    }

    private NodeResolverBinding node(Long id, Long nodeId, String code, String target,
                                     WorkflowResolverBinding resolver) {
        String ruleHash = hasher.ruleHash("USER", "DIRECT", "USER", target, target);
        return new NodeResolverBinding(id, 5L, resolver.id(), 3L, 4L, nodeId, code,
                AssignmentStrategy.Type.USER, ResolverMode.DIRECT, AssignmentStrategy.Type.USER,
                target, WorkflowResolverBindingHasher.RULE_VERSION, target, ruleHash,
                hasher.nodeHash(code, resolver, "USER", target, ruleHash),
                WorkflowResolverBindingSet.Status.FROZEN,
                LocalDateTime.of(2026, 8, 12, 9, 0), "test", 0);
    }

    private String hash(String value) { return ResolverContractHash.sha256(value).value(); }
}
