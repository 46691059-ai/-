package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowDefinitionApplicationService;
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
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowVersionContentHasher;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowVersionResolverBindingReleaseIntegrationTest {
    private final WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
    private final WorkflowVersionRepository versions = mock(WorkflowVersionRepository.class);
    private final WorkflowNodeRepository nodes = mock(WorkflowNodeRepository.class);
    private final WorkflowIdentityGenerator ids = mock(WorkflowIdentityGenerator.class);
    private final WorkflowVersionReleaseRepository releases = mock(WorkflowVersionReleaseRepository.class);
    private final WorkflowTransitionRepository transitions = mock(WorkflowTransitionRepository.class);
    private final VersionNodeResolverBindingRepository bindings =
            mock(VersionNodeResolverBindingRepository.class);
    private final ResolverBindingManifestRepository manifests =
            mock(ResolverBindingManifestRepository.class);
    private final CurrentSecurityContext security = mock(CurrentSecurityContext.class);
    private WorkflowDefinitionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionApplicationService(
                definitions, versions, nodes, ids, releases, transitions,
                bindings, manifests, registry(), security);
        when(security.principal()).thenReturn(new SecurityPrincipal(
                7L, "publisher", 8L, Set.of(8L), false, false, 0));
    }

    @Test
    void capableVersionMustPublishManifestCombinedHashAndMatchingReleaseAtomically() {
        Fixture fixture = arrangeHappyPath();
        when(ids.nextId()).thenReturn(700L, 701L);

        var result = service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, "RC2 approved"));

        var manifestComputation = ResolverBindingManifestCanonical.compute(fixture.bindings());
        String graphHash = new WorkflowVersionContentHasher().hashGraph(
                fixture.definition(), fixture.target(), fixture.nodes(), fixture.transitions());
        String expectedContentHash = new WorkflowCombinedContentHasher().compute(
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                graphHash, manifestComputation).contentHash();
        assertThat(result.version().status()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
        assertThat(result.version().contentHash()).isEqualTo(expectedContentHash);
        assertThat(result.version().resolverBindingManifestHash())
                .isEqualTo(manifestComputation.manifestHash());
        assertThat(result.version().resolverBindingCount()).isEqualTo(2);

        ArgumentCaptor<ResolverBindingManifest> manifest =
                ArgumentCaptor.forClass(ResolverBindingManifest.class);
        verify(manifests).append(manifest.capture());
        assertThat(manifest.getValue().id()).isEqualTo(700L);
        assertThat(manifest.getValue().manifestHash()).isEqualTo(manifestComputation.manifestHash());

        ArgumentCaptor<WorkflowVersionRelease> release =
                ArgumentCaptor.forClass(WorkflowVersionRelease.class);
        verify(releases).save(release.capture());
        assertThat(release.getValue().contentHash()).isEqualTo(result.version().contentHash());
        assertThat(release.getValue().resolverBindingModel())
                .isEqualTo(result.version().resolverBindingModel());
        assertThat(release.getValue().resolverBindingManifestHash())
                .isEqualTo(result.version().resolverBindingManifestHash());
        assertThat(release.getValue().resolverBindingCount())
                .isEqualTo(result.version().resolverBindingCount());

        var order = inOrder(bindings, versions, manifests, definitions, releases);
        order.verify(bindings, times(2)).update(any());
        order.verify(versions).prepareResolverBindingSnapshot(any(), eq(0));
        order.verify(manifests).append(any());
        order.verify(versions).updateState(any(), eq(WorkflowVersion.Status.DRAFT), eq(1));
        order.verify(definitions).updateCurrentVersion(any(), eq(0));
        order.verify(releases).save(any());
    }

    @Test
    void missingBindingMustFailClosedWithoutManifestOrPublication() {
        Fixture fixture = arrangeHappyPath();
        when(bindings.findByVersionId(20L)).thenReturn(List.of(fixture.bindings().getFirst()));

        assertThatThrownBy(() -> service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("missing");
        verify(manifests, never()).append(any());
        verify(versions, never()).prepareResolverBindingSnapshot(any(), any(Integer.class));
        verify(releases, never()).save(any());
    }

    @Test
    void resolverContractDriftMustFailClosedWithoutRewritingDraft() {
        Fixture fixture = arrangeHappyPath();
        VersionNodeResolverBinding drifted = binding(
                fixture.nodes().getFirst().id(), 1, "ROLE_APPROVER", "a".repeat(64));
        when(bindings.findByVersionId(20L)).thenReturn(
                List.of(drifted, fixture.bindings().get(1)));

        assertThatThrownBy(() -> service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("contract drift");
        verify(bindings, never()).update(any());
        verify(manifests, never()).append(any());
        verify(versions, never()).prepareResolverBindingSnapshot(any(), any(Integer.class));
        verify(releases, never()).save(any());
    }

    @Test
    void staleConcurrentPublisherMustBeRejectedBeforeASecondReleaseIsCreated() {
        Fixture fixture = arrangeHappyPath();
        WorkflowDefinition activated = fixture.definition().activateVersion(20L);
        when(definitions.findByIdForUpdate(1L))
                .thenReturn(Optional.of(fixture.definition()), Optional.of(activated));
        when(ids.nextId()).thenReturn(700L, 701L);

        service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, "first publisher"));

        assertThatThrownBy(() -> service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, "stale publisher")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("workflow definition was changed concurrently");
        verify(manifests).append(any());
        verify(releases).save(any());
        verify(versions).findByIdForUpdate(20L);
    }

    @Test
    void failedDraftPublicationCanBeRetriedWithoutDuplicateReleaseEvidence() {
        arrangeHappyPath();
        when(ids.nextId()).thenReturn(700L, 700L, 701L);
        doThrow(new IllegalStateException("temporary manifest failure"))
                .doNothing().when(manifests).append(any());

        assertThatThrownBy(() -> service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, "first attempt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporary manifest failure");

        var retried = service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(0, 0, "retry"));
        assertThat(retried.version().status()).isEqualTo(WorkflowVersion.Status.PUBLISHED);
        verify(manifests, times(2)).append(any());
        verify(releases).save(any());
    }

    @Test
    void alreadyPublishedVersionMustFailFastWithoutDuplicateManifestOrRelease() {
        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "RC2", "RC2", "TEST", 10L, 8L,
                WorkflowDefinition.Status.ACTIVE, 20L, null, 1);
        WorkflowVersion published = capableDraft()
                .prepareResolverBindingSnapshot("a".repeat(64), 1,
                        "VERSION_RESOLVER_BINDING_MANIFEST_V1")
                .publish("b".repeat(64), 7L, LocalDateTime.now());
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(20L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.publishVersion(1L, 20L,
                new PublishWorkflowVersionCommand(1, published.version(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only DRAFT");
        verify(manifests, never()).append(any());
        verify(releases, never()).save(any());
    }

    private Fixture arrangeHappyPath() {
        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "RC2", "RC2", "TEST", 10L, 8L,
                WorkflowDefinition.Status.DRAFT, null, null, 0);
        WorkflowVersion target = capableDraft();
        List<WorkflowNode> nodeList = List.of(node(30L, 1), node(40L, 2));
        List<WorkflowTransition> transitionList = List.of(new WorkflowTransition(
                50L, 20L, "A_TO_B", "A to B", 30L, 40L,
                WorkflowTransition.TriggerType.APPROVE, WorkflowTransition.RouteType.DIRECT,
                1, null, true, 0));
        List<VersionNodeResolverBinding> bindingList = List.of(
                binding(30L, 1, "ROLE_APPROVER", RoleDirectoryResolver.CONTRACT_HASH.value()),
                binding(40L, 1, "ROLE_REVIEWER", RoleDirectoryResolver.CONTRACT_HASH.value()));
        when(definitions.findByIdForUpdate(1L)).thenReturn(Optional.of(definition));
        when(versions.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
        when(nodes.findByVersionId(20L)).thenReturn(nodeList);
        when(transitions.findByVersionId(20L)).thenReturn(transitionList);
        when(bindings.findByVersionId(20L)).thenReturn(bindingList);
        when(versions.prepareResolverBindingSnapshot(any(), eq(0))).thenReturn(true);
        when(versions.updateState(any(), eq(WorkflowVersion.Status.DRAFT), eq(1))).thenReturn(true);
        when(definitions.updateCurrentVersion(any(), eq(0))).thenReturn(true);
        return new Fixture(definition, target, nodeList, transitionList, bindingList);
    }

    private WorkflowVersion capableDraft() {
        return new WorkflowVersion(
                20L, 1L, 1, WorkflowVersion.Status.DRAFT, "2.0", null, null,
                null, null, null, null, null, WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                null, 0, null, 0);
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
            Long nodeId, int order, String roleCode, String contractHash) {
        return new VersionNodeResolverBinding(
                nodeId * 10 + order, 1L, 20L, nodeId, order,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of(contractHash), AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE, roleCode,
                OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "0".repeat(64), 0);
    }

    private ResolverRegistry registry() {
        var explicit = new ExplicitUserResolver();
        return new ResolverRegistry(
                List.of(explicit.descriptor(), RoleDirectoryResolver.PREPARED_DESCRIPTOR),
                List.of(explicit));
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }

    private record Fixture(
            WorkflowDefinition definition, WorkflowVersion target,
            List<WorkflowNode> nodes, List<WorkflowTransition> transitions,
            List<VersionNodeResolverBinding> bindings) { }
}
