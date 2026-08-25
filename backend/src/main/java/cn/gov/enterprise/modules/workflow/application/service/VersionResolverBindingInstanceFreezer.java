package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingComputation;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowInstance;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.service.ResolverBindingCoveragePolicy;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCanonicalHashSupport;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Converts immutable published Version bindings into immutable Instance facts without Directory access. */
@Service
public class VersionResolverBindingInstanceFreezer {
    private final VersionNodeResolverBindingRepository bindings;
    private final ResolverBindingManifestRepository manifests;
    private final WorkflowVersionReleaseRepository releases;
    private final WorkflowIdentityGenerator ids;
    private final ResolverRegistry registry;
    private final ResolverBindingCoveragePolicy coverage = new ResolverBindingCoveragePolicy();
    private final WorkflowCombinedContentHasher combinedHasher = new WorkflowCombinedContentHasher();

    public VersionResolverBindingInstanceFreezer(
            VersionNodeResolverBindingRepository bindings,
            ResolverBindingManifestRepository manifests,
            WorkflowVersionReleaseRepository releases,
            WorkflowIdentityGenerator ids, ResolverRegistry registry) {
        this.bindings = bindings;
        this.manifests = manifests;
        this.releases = releases;
        this.ids = ids;
        this.registry = registry;
    }

    public WorkflowResolverBindingApplicationService.FrozenBindings prepare(
            WorkflowInstance instance, WorkflowDefinition definition,
            WorkflowVersion version, List<WorkflowNode> nodes, String graphHash,
            LocalDateTime frozenTime, String auditInfo) {
        requirePublishedCapable(definition, version);
        ResolverBindingManifest manifest = manifests.findByDefinitionVersionId(version.id())
                .orElseThrow(() -> invalid("published Version resolver Manifest is missing"));
        WorkflowVersionRelease release = releases.findByDefinitionIdAndPublishedVersionId(
                        definition.id(), version.id())
                .orElseThrow(() -> invalid("published Version Release evidence is missing"));
        List<VersionNodeResolverBinding> source = bindings.findByVersionId(version.id());
        try {
            coverage.validate(definition.id(), version.id(), nodes, source);
        } catch (IllegalStateException exception) {
            throw invalid(exception.getMessage());
        }
        var computed = ResolverBindingManifestCanonical.compute(source);
        if (computed.bindingCount() != version.resolverBindingCount()
                || computed.bindingCount() != manifest.bindingCount()
                || !computed.manifestHash().equals(version.resolverBindingManifestHash())
                || !computed.manifestHash().equals(manifest.manifestHash())
                || !computed.canonicalVersion().equals(version.resolverBindingCanonicalVersion())
                || !computed.canonicalVersion().equals(manifest.canonicalVersion())) {
            throw invalid("published Version resolver Manifest integrity check failed");
        }
        if (!release.contentHash().equals(version.contentHash())
                || release.resolverBindingModel() != version.resolverBindingModel()
                || release.resolverBindingCount() != version.resolverBindingCount()
                || !computed.manifestHash().equals(release.resolverBindingManifestHash())
                || !computed.canonicalVersion().equals(release.resolverBindingCanonicalVersion())) {
            throw invalid("Version Release resolver snapshot integrity check failed");
        }
        String combined = combinedHasher.compute(
                version.resolverBindingModel(), graphHash, computed).contentHash();
        if (!combined.equals(version.contentHash())) {
            throw invalid("published Workflow combined content hash does not match runtime graph");
        }

        for (VersionNodeResolverBindingComputation item : computed.orderedBindings()) {
            VersionNodeResolverBinding binding = item.binding();
            if (!item.bindingHash().equals(binding.bindingHash())) {
                throw invalid("published Version Binding hash drift");
            }
            if (binding.organizationScopeType() != OrganizationScopeType.FIXED_ORG) {
                throw invalid("unsupported Instance freeze organization scope");
            }
            AssignmentResolverDescriptor descriptor = registry.requireDescriptor(
                    binding.resolverCode(), binding.resolverVersion());
            if (!descriptor.contractHash().equals(binding.resolverContractHash())
                    || descriptor.strategyType() != binding.strategyType()
                    || descriptor.mode() != binding.resolverMode()
                    || (descriptor.status() != ResolverStatus.PREPARED
                    && descriptor.status() != ResolverStatus.ACTIVE)) {
                throw invalid("Resolver contract drift blocks Instance creation");
            }
        }

        long setId = ids.nextId();
        Map<ResolverKey, List<VersionNodeResolverBindingComputation>> groups = new LinkedHashMap<>();
        computed.orderedBindings().forEach(item -> groups.computeIfAbsent(
                ResolverKey.from(item.binding()), ignored -> new ArrayList<>()).add(item));
        Map<ResolverKey, WorkflowResolverBinding> frozenResolvers = new LinkedHashMap<>();
        for (Map.Entry<ResolverKey, List<VersionNodeResolverBindingComputation>> entry : groups.entrySet()) {
            VersionNodeResolverBinding first = entry.getValue().getFirst().binding();
            String ruleHash = hashOrdered(entry.getValue().stream()
                    .map(VersionNodeResolverBindingComputation::bindingHash).toList());
            frozenResolvers.put(entry.getKey(), new WorkflowResolverBinding(
                    ids.nextId(), setId, instance.id(), version.id(), first.resolverCode(),
                    first.resolverVersion(), first.strategyType(), first.resolverMode(),
                    first.resolverContractHash(), ruleHash, WorkflowResolverBindingSet.Status.FROZEN,
                    frozenTime, auditInfo, 0));
        }
        Map<Long, WorkflowNode> nodeIndex = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(WorkflowNode::id, item -> item));
        List<NodeResolverBinding> frozenNodes = computed.orderedBindings().stream().map(item -> {
            VersionNodeResolverBinding binding = item.binding();
            WorkflowNode node = nodeIndex.get(binding.nodeId());
            WorkflowResolverBinding resolver = frozenResolvers.get(ResolverKey.from(binding));
            String target = "{\"roleCode\":\"" + binding.roleCode()
                    + "\",\"organizationId\":" + binding.organizationId() + "}";
            String rule = WorkflowCanonicalHashSupport.canonical(
                    binding.bindingSchemaVersion(), binding.roleCode(),
                    binding.organizationScopeType(), binding.organizationId(),
                    binding.effectiveTimePolicy(), binding.bindingHash());
            return new NodeResolverBinding(
                    ids.nextId(), setId, resolver.id(), instance.id(), version.id(), node.id(),
                    node.nodeCode(), binding.strategyType(), binding.resolverMode(),
                    binding.targetType(), target, binding.bindingSchemaVersion(), rule,
                    binding.bindingHash(), binding.bindingHash(), binding.id(),
                    binding.bindingOrder(), binding.bindingHash(), binding.resolverContractHash(),
                    binding.roleCode(), binding.organizationScopeType(), binding.organizationId(),
                    binding.effectiveTimePolicy(), binding.bindingSchemaVersion(),
                    WorkflowResolverBindingSet.Status.FROZEN, frozenTime, auditInfo, 0);
        }).toList();
        WorkflowResolverBindingSet set = new WorkflowResolverBindingSet(
                setId, instance.id(), definition.id(), version.id(),
                computed.canonicalVersion(), computed.manifestHash(), computed.bindingCount(),
                WorkflowResolverBindingSet.Status.FROZEN, frozenTime, auditInfo, 0);
        return new WorkflowResolverBindingApplicationService.FrozenBindings(
                set, List.copyOf(frozenResolvers.values()), frozenNodes);
    }

    private void requirePublishedCapable(WorkflowDefinition definition, WorkflowVersion version) {
        if (version.status() != WorkflowVersion.Status.PUBLISHED
                || !definition.id().equals(version.definitionId())
                || version.resolverBindingModel()
                != ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE
                || version.resolverBindingCount() < 1
                || version.resolverBindingManifestHash() == null) {
            throw invalid("Workflow Version is not a complete published resolver-binding Version");
        }
    }

    private String hashOrdered(List<String> hashes) {
        String[] ordered = hashes.stream().sorted().toArray(String[]::new);
        return WorkflowCanonicalHashSupport.sha256(
                WorkflowCanonicalHashSupport.canonical((Object[]) ordered));
    }

    private BusinessException invalid(String message) {
        return new BusinessException("B2650", message);
    }

    private record ResolverKey(String code, String version) {
        static ResolverKey from(VersionNodeResolverBinding value) {
            return new ResolverKey(value.resolverCode().value(), value.resolverVersion().value());
        }
    }
}
