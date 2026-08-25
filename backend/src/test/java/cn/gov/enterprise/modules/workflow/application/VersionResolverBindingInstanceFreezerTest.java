package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.VersionResolverBindingInstanceFreezer;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingCanonical;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VersionResolverBindingInstanceFreezerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 9, 0);
    private static final String GRAPH_HASH = "1".repeat(64);
    private final VersionNodeResolverBindingRepository bindings = mock(VersionNodeResolverBindingRepository.class);
    private final ResolverBindingManifestRepository manifests = mock(ResolverBindingManifestRepository.class);
    private final WorkflowVersionReleaseRepository releases = mock(WorkflowVersionReleaseRepository.class);
    private final WorkflowIdentityGenerator ids = mock(WorkflowIdentityGenerator.class);
    private VersionResolverBindingInstanceFreezer freezer;

    @BeforeEach
    void setUp() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        freezer = new VersionResolverBindingInstanceFreezer(bindings, manifests, releases, ids,
                new ResolverRegistry(
                        List.of(explicit.descriptor(), RoleDirectoryResolver.PREPARED_DESCRIPTOR),
                        List.of(explicit)));
    }

    @Test
    void publishedRoleBindingsMustFreezeAllBindingsInStableOrderWithoutDirectoryData() {
        List<VersionNodeResolverBinding> source = List.of(
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L),
                binding(30L, 2, "ROLE_OBSERVER", OrganizationScopeType.FIXED_ORG, 9L),
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L));
        Fixture fixture = arrange(source);
        when(ids.nextId()).thenReturn(900L, 901L, 902L, 903L, 904L);

        var result = freezer.prepare(fixture.instance(), fixture.definition(), fixture.version(),
                fixture.nodes(), GRAPH_HASH, NOW, "RC2-S5");

        assertThat(result.set().bindingCount()).isEqualTo(3);
        assertThat(result.set().bindingSetHash()).isEqualTo(fixture.manifest().manifestHash());
        assertThat(result.resolvers()).hasSize(1);
        assertThat(result.nodes()).extracting(item -> item.nodeId() + ":" + item.versionBindingOrder())
                .containsExactly("30:1", "30:2", "40:1");
        assertThat(result.nodes()).allSatisfy(item -> {
            assertThat(item.strategyType()).isEqualTo(AssignmentStrategy.Type.ROLE);
            assertThat(item.resolverMode()).isEqualTo(ResolverMode.CANDIDATE_POOL);
            assertThat(item.versionBindingHash()).isEqualTo(item.nodeBindingHash());
            assertThat(item.resolvedOrganizationId()).isPositive();
        });
    }

    @Test
    void missingManifestMustFailClosed() {
        Fixture fixture = arrange(List.of(
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L),
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L)));
        when(manifests.findByDefinitionVersionId(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Manifest is missing");
    }

    @Test
    void manifestCountOrHashMismatchMustFailClosed() {
        Fixture fixture = arrange(List.of(
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L),
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L)));
        ResolverBindingManifest mismatched = new ResolverBindingManifest(
                fixture.manifest().id(), fixture.manifest().definitionId(),
                fixture.manifest().definitionVersionId(), fixture.manifest().canonicalVersion(),
                fixture.manifest().bindingCount() + 1, "9".repeat(64),
                fixture.manifest().releasedBy(), fixture.manifest().releasedTime());
        when(manifests.findByDefinitionVersionId(20L)).thenReturn(Optional.of(mismatched));
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Manifest integrity");
    }

    @Test
    void missingRequiredNodeBindingMustFailClosed() {
        Fixture fixture = arrange(List.of(
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L)));
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void nonPublishedVersionMustFailClosedBeforeFreeze() {
        Fixture fixture = arrange(List.of(
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L),
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L)));
        WorkflowVersion draft = new WorkflowVersion(
                fixture.version().id(), fixture.version().definitionId(),
                fixture.version().versionNo(), WorkflowVersion.Status.DRAFT, "2.0",
                fixture.version().contentHash(), null, null, null, null, null, null,
                fixture.version().engineMode(), fixture.version().contentHashAlgorithm(),
                fixture.version().resolverBindingModel(),
                fixture.version().resolverBindingManifestHash(),
                fixture.version().resolverBindingCount(),
                fixture.version().resolverBindingCanonicalVersion(), 0);
        assertThatThrownBy(() -> freezer.prepare(
                fixture.instance(), fixture.definition(), draft, fixture.nodes(),
                GRAPH_HASH, NOW, "RC2-S5"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not a complete published");
    }

    @Test
    void storedBindingHashDriftMustFailClosed() {
        VersionNodeResolverBinding drifted = rawBinding(
                30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L,
                RoleDirectoryResolver.CONTRACT_HASH.value(), "0".repeat(64));
        Fixture fixture = arrange(List.of(drifted,
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L)));
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Binding hash drift");
    }

    @Test
    void resolverContractDriftMustFailClosed() {
        Fixture fixture = arrange(List.of(
                rawBinding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG, 8L,
                        "a".repeat(64), null),
                rawBinding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L,
                        "a".repeat(64), null)));
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("contract drift");
    }

    @Test
    void instanceBusinessOrganizationMustFailClosedWithoutAuthoritativeSource() {
        Fixture fixture = arrange(List.of(
                binding(30L, 1, "ROLE_APPROVER", OrganizationScopeType.INSTANCE_BUSINESS_ORG, null),
                binding(40L, 1, "ROLE_REVIEWER", OrganizationScopeType.FIXED_ORG, 8L)));
        assertThatThrownBy(() -> prepare(fixture)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("unsupported Instance freeze organization scope");
    }

    private Object prepare(Fixture fixture) {
        return freezer.prepare(fixture.instance(), fixture.definition(), fixture.version(),
                fixture.nodes(), GRAPH_HASH, NOW, "RC2-S5");
    }

    private Fixture arrange(List<VersionNodeResolverBinding> source) {
        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "RC2", "RC2", "TEST", 10L, 8L,
                WorkflowDefinition.Status.ACTIVE, 20L, null, 1);
        List<WorkflowNode> nodes = List.of(node(30L, 1), node(40L, 2));
        var computation = ResolverBindingManifestCanonical.compute(source);
        String contentHash = new WorkflowCombinedContentHasher().compute(
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                GRAPH_HASH, computation).contentHash();
        WorkflowVersion version = new WorkflowVersion(
                20L, 1L, 2, WorkflowVersion.Status.PUBLISHED, "2.0", contentHash, null,
                NOW.minusMinutes(1), null, 7L, NOW.minusMinutes(1), null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                computation.manifestHash(), computation.bindingCount(),
                computation.canonicalVersion(), 2);
        ResolverBindingManifest manifest = new ResolverBindingManifest(
                60L, 1L, 20L, computation.canonicalVersion(), computation.bindingCount(),
                computation.manifestHash(), 7L, NOW.minusMinutes(1));
        WorkflowVersionRelease release = new WorkflowVersionRelease(
                61L, 1L, null, 20L, 2, contentHash, 7L, 8L,
                NOW.minusMinutes(1), null, "RC2", WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                computation.manifestHash(), computation.bindingCount(), computation.canonicalVersion());
        WorkflowInstance instance = WorkflowInstance.runningLinear(
                70L, "WFI-70", definition, version, "TEST", "B-1", "TEST:B-1", 10L,
                null, null, 1, 7L, 8L, 30L, "{}", "idem", "2".repeat(64), null, NOW);
        when(bindings.findByVersionId(20L)).thenReturn(source);
        when(manifests.findByDefinitionVersionId(20L)).thenReturn(Optional.of(manifest));
        when(releases.findByDefinitionIdAndPublishedVersionId(1L, 20L))
                .thenReturn(Optional.of(release));
        return new Fixture(definition, version, nodes, manifest, instance);
    }

    private WorkflowNode node(Long id, int order) {
        return new WorkflowNode(id, 20L, "NODE_" + id, "Node " + id,
                WorkflowNode.NodeType.APPROVAL, order,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL,
                WorkflowNode.ApprovalMode.SINGLE, null,
                WorkflowNode.AssignmentRuleType.RULE, "{}", null, null,
                null, true, true, 0);
    }

    private VersionNodeResolverBinding binding(
            Long nodeId, int order, String roleCode,
            OrganizationScopeType scope, Long organizationId) {
        return rawBinding(nodeId, order, roleCode, scope, organizationId,
                RoleDirectoryResolver.CONTRACT_HASH.value(), null);
    }

    private VersionNodeResolverBinding rawBinding(
            Long nodeId, int order, String roleCode, OrganizationScopeType scope,
            Long organizationId, String contractHash, String storedHash) {
        VersionNodeResolverBinding value = new VersionNodeResolverBinding(
                nodeId * 10 + order, 1L, 20L, nodeId, order,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of(contractHash), AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE, roleCode,
                scope, organizationId, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION,
                storedHash == null ? "0".repeat(64) : storedHash, 0);
        if (storedHash != null) return value;
        return value.withBindingHash(VersionNodeResolverBindingCanonical.compute(value).bindingHash());
    }

    private record Fixture(
            WorkflowDefinition definition, WorkflowVersion version, List<WorkflowNode> nodes,
            ResolverBindingManifest manifest, WorkflowInstance instance) { }
}
