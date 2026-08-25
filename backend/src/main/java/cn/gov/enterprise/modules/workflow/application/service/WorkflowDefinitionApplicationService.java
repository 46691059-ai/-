package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowDefinitionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.vo.WorkflowDefinitionDetail;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestCanonical;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifestComputation;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBindingComputation;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowDefinitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionReleaseRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTransitionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTransition;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowLinearGraphValidator;
import cn.gov.enterprise.modules.workflow.domain.service.ResolverBindingCoveragePolicy;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowCombinedContentHasher;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowVersionContentHasher;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

@Service
public class WorkflowDefinitionApplicationService {
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final WorkflowVersionReleaseRepository releaseRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final VersionNodeResolverBindingRepository bindingRepository;
    private final ResolverBindingManifestRepository manifestRepository;
    private final ResolverRegistry resolverRegistry;
    private final CurrentSecurityContext securityContext;
    private final WorkflowVersionContentHasher contentHasher = new WorkflowVersionContentHasher();
    private final WorkflowLinearGraphValidator graphValidator = new WorkflowLinearGraphValidator();
    private final ResolverBindingCoveragePolicy bindingCoveragePolicy =
            new ResolverBindingCoveragePolicy();
    private final WorkflowCombinedContentHasher combinedContentHasher =
            new WorkflowCombinedContentHasher();

    @Autowired
    public WorkflowDefinitionApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            WorkflowVersionReleaseRepository releaseRepository,
            WorkflowTransitionRepository transitionRepository,
            VersionNodeResolverBindingRepository bindingRepository,
            ResolverBindingManifestRepository manifestRepository,
            ResolverRegistry resolverRegistry,
            CurrentSecurityContext securityContext) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.identityGenerator = identityGenerator;
        this.releaseRepository = releaseRepository;
        this.transitionRepository = transitionRepository;
        this.bindingRepository = bindingRepository;
        this.manifestRepository = manifestRepository;
        this.resolverRegistry = resolverRegistry;
        this.securityContext = securityContext;
    }

    /** Compatibility constructor retained for legacy-focused unit tests and adapters. */
    public WorkflowDefinitionApplicationService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowVersionRepository versionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowIdentityGenerator identityGenerator,
            WorkflowVersionReleaseRepository releaseRepository,
            CurrentSecurityContext securityContext) {
        this(definitionRepository, versionRepository, nodeRepository, identityGenerator,
                releaseRepository, null, null, null, ResolverRegistry.explicitUserOnly(),
                securityContext);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:create')")
    public WorkflowDefinition createDefinition(CreateWorkflowDefinitionCommand command) {
        if (definitionRepository.existsByEnterpriseIdAndCode(command.enterpriseId(), command.definitionCode())) {
            throw new BusinessException("B2509", "workflow definition code already exists in enterprise");
        }
        try {
            WorkflowDefinition definition = WorkflowDefinition.draft(identityGenerator.nextId(),
                    command.definitionCode(), command.definitionName(), command.businessType(),
                    command.enterpriseId(), command.ownerOrgId(), command.description());
            definitionRepository.save(definition);
            return definition;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public WorkflowDefinitionDetail queryDefinition(Long definitionId) {
        WorkflowDefinition definition = requireDefinition(definitionId);
        List<WorkflowVersion> versions = versionRepository.findByDefinitionId(definitionId);
        Map<Long, List<WorkflowNode>> nodesByVersion = nodeRepository
                .findByVersionIds(versions.stream().map(WorkflowVersion::id).toList())
                .stream().collect(Collectors.groupingBy(WorkflowNode::versionId));
        List<WorkflowDefinitionDetail.VersionDetail> details = versions.stream()
                .map(version -> new WorkflowDefinitionDetail.VersionDetail(
                        version, nodesByVersion.getOrDefault(version.id(), List.of())))
                .toList();
        return new WorkflowDefinitionDetail(definition, details);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:definition:view')")
    public WorkflowDefinitionDetail.VersionDetail queryVersion(Long definitionId, Long versionId) {
        requireDefinition(definitionId);
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> notFound("workflow version"));
        requireVersionOwner(definitionId, version);
        return new WorkflowDefinitionDetail.VersionDetail(version, nodeRepository.findByVersionId(versionId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public WorkflowVersion createVersion(Long definitionId, CreateWorkflowVersionCommand command) {
        definitionRepository.findByIdForUpdate(definitionId)
                .orElseThrow(() -> notFound("workflow definition"));
        WorkflowVersion source = null;
        if (command.sourceVersionId() != null) {
            source = versionRepository.findById(command.sourceVersionId())
                    .orElseThrow(() -> notFound("source workflow version"));
            if (!definitionId.equals(source.definitionId())) {
                throw new BusinessException("B2500", "source version belongs to another definition");
            }
        }
        try {
            WorkflowVersion created = WorkflowVersion.draft(identityGenerator.nextId(), definitionId,
                    versionRepository.nextVersionNo(definitionId), command.schemaVersion(),
                    command.changeNote(), command.sourceVersionId());
            versionRepository.save(created);
            if (source != null) {
                List<WorkflowNode> copied = nodeRepository.findByVersionId(source.id()).stream()
                        .map(node -> node.copyTo(identityGenerator.nextId(), created.id())).toList();
                if (!copied.isEmpty()) nodeRepository.replaceNodes(created.id(), copied);
            }
            return created;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public List<WorkflowNode> replaceNodes(
            Long definitionId, Long versionId, ReplaceWorkflowNodesCommand command) {
        requireDefinition(definitionId);
        WorkflowVersion workflowVersion = versionRepository.findByIdForUpdate(versionId)
                .orElseThrow(() -> notFound("workflow version"));
        if (!definitionId.equals(workflowVersion.definitionId())) {
            throw new BusinessException("B2500", "workflow version belongs to another definition");
        }
        if (!workflowVersion.isEditable()) {
            throw new BusinessException("B2503", "published or retired workflow versions are immutable");
        }
        ensureUniqueNodeKeys(command.nodes());
        try {
            List<WorkflowNode> nodes = command.nodes().stream().map(node -> new WorkflowNode(
                    identityGenerator.nextId(), versionId, node.nodeCode(), node.nodeName(), node.nodeType(),
                    node.nodeOrder(), node.governanceNodeType(), node.approvalMode(), node.approvalThreshold(),
                    node.assignmentRuleType(), node.assignmentRuleConfig(), node.entryConditionConfig(),
                    node.completionConditionConfig(), node.timeoutMinutes(), node.withdrawAllowed(),
                    node.enabled(), 0)).toList();
            nodeRepository.replaceNodes(versionId, nodes);
            return nodes;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:publish')")
    public WorkflowDefinitionDetail.VersionDetail publishVersion(
            Long definitionId, Long versionId, PublishWorkflowVersionCommand command) {
        WorkflowDefinition definition = definitionRepository.findByIdForUpdate(definitionId)
                .orElseThrow(() -> notFound("workflow definition"));
        if (definition.version() != command.expectedDefinitionVersion()) {
            throw concurrentChange("workflow definition");
        }
        WorkflowVersion target = versionRepository.findByIdForUpdate(versionId)
                .orElseThrow(() -> notFound("workflow version"));
        requireVersionOwner(definitionId, target);
        if (target.version() != command.expectedVersion()) throw concurrentChange("workflow version");

        SecurityPrincipal principal = securityContext.principal();
        requireManagementScope(definition, principal);
        List<WorkflowNode> nodes = nodeRepository.findByVersionId(versionId);
        List<WorkflowTransition> transitions = target.engineMode() == WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                ? requireTransitionRepository().findByVersionId(versionId) : List.of();
        validatePublishable(definition, target, nodes, transitions);
        LocalDateTime publishedTime = LocalDateTime.now();

        WorkflowVersion prepared = target;
        ResolverBindingManifestComputation manifestComputation = null;
        if (target.resolverBindingModel()
                == ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE) {
            List<VersionNodeResolverBinding> bindings = requireBindingRepository()
                    .findByVersionId(versionId);
            validateBindings(definition, target, nodes, bindings);
            manifestComputation = ResolverBindingManifestCanonical.compute(bindings);
            persistComputedBindingHashes(manifestComputation);
            prepared = target.prepareResolverBindingSnapshot(
                    manifestComputation.manifestHash(), manifestComputation.bindingCount(),
                    manifestComputation.canonicalVersion());
            if (!versionRepository.prepareResolverBindingSnapshot(prepared, target.version())) {
                throw concurrentChange("workflow version resolver binding snapshot");
            }
        }

        String graphHash = target.engineMode() == WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                ? contentHasher.hashGraph(definition, target, nodes, transitions)
                : contentHasher.hash(definition, target, nodes);
        String contentHash = combinedContentHasher.compute(
                target.resolverBindingModel(), graphHash, manifestComputation).contentHash();

        WorkflowVersion previous = null;
        if (definition.currentVersionId() != null) {
            previous = versionRepository.findByIdForUpdate(definition.currentVersionId())
                    .orElseThrow(() -> notFound("current workflow version"));
            requireVersionOwner(definitionId, previous);
            if (previous.status() != WorkflowVersion.Status.PUBLISHED) {
                throw new BusinessException("B2503", "workflow definition current version is not PUBLISHED");
            }
        }

        if (manifestComputation != null) {
            requireManifestRepository().append(new ResolverBindingManifest(
                    identityGenerator.nextId(), definitionId, versionId,
                    manifestComputation.canonicalVersion(), manifestComputation.bindingCount(),
                    manifestComputation.manifestHash(), principal.userId(), publishedTime));
        }

        WorkflowVersion published = prepared.publish(contentHash, principal.userId(), publishedTime);
        if (!versionRepository.updateState(published, WorkflowVersion.Status.DRAFT, prepared.version())) {
            throw concurrentChange("workflow version");
        }
        if (previous != null) {
            WorkflowVersion retired = previous.retire(publishedTime);
            if (!versionRepository.updateState(
                    retired, WorkflowVersion.Status.PUBLISHED, previous.version())) {
                throw concurrentChange("current workflow version");
            }
        }
        WorkflowDefinition activated = definition.activateVersion(published.id());
        if (!definitionRepository.updateCurrentVersion(activated, definition.version())) {
            throw concurrentChange("workflow definition");
        }
        releaseRepository.save(new WorkflowVersionRelease(identityGenerator.nextId(), definitionId,
                previous == null ? null : previous.id(), published.id(), published.versionNo(), contentHash,
                principal.userId(), principal.orgId(), publishedTime, MDC.get("traceId"),
                published.engineMode().name() + " validated; previous="
                        + (previous == null ? "NONE" : previous.id())
                        + "; resolverBindingModel=" + published.resolverBindingModel().name()
                        + "; bindingCount=" + published.resolverBindingCount()
                        + (published.resolverBindingManifestHash() == null ? ""
                        : "; manifestHash=" + published.resolverBindingManifestHash())
                        + "; contentHash=" + published.contentHash()
                        + (command.reason() == null ? "" : "; reason=" + command.reason().trim()),
                published.engineMode(), published.contentHashAlgorithm(),
                published.resolverBindingModel(), published.resolverBindingManifestHash(),
                published.resolverBindingCount(), published.resolverBindingCanonicalVersion()));
        return new WorkflowDefinitionDetail.VersionDetail(published, nodes);
    }

    private void validateBindings(
            WorkflowDefinition definition, WorkflowVersion version,
            List<WorkflowNode> nodes, List<VersionNodeResolverBinding> bindings) {
        try {
            bindingCoveragePolicy.validate(definition.id(), version.id(), nodes, bindings);
        } catch (IllegalStateException exception) {
            throw invalid(exception);
        }
        for (VersionNodeResolverBinding binding : bindings) {
            try {
                AssignmentResolverDescriptor descriptor = requireResolverRegistry()
                        .requireDescriptor(binding.resolverCode(), binding.resolverVersion());
                if (!descriptor.contractHash().equals(binding.resolverContractHash())) {
                    throw new BusinessException("B2624", "resolver contract drift blocks publication");
                }
                if (descriptor.strategyType() != binding.strategyType()
                        || descriptor.mode() != binding.resolverMode()
                        || (descriptor.status() != ResolverStatus.PREPARED
                        && descriptor.status() != ResolverStatus.ACTIVE)) {
                    throw new BusinessException("B2620", "resolver descriptor is incompatible with binding");
                }
            } catch (AssignmentResolverRegistryException exception) {
                throw new BusinessException("B2624", "resolver does not exist for Version binding");
            }
        }
    }

    private void persistComputedBindingHashes(
            ResolverBindingManifestComputation manifest) {
        for (VersionNodeResolverBindingComputation computation : manifest.orderedBindings()) {
            VersionNodeResolverBinding binding = computation.binding();
            if (!computation.bindingHash().equals(binding.bindingHash())) {
                requireBindingRepository().update(
                        binding.withBindingHash(computation.bindingHash()));
            }
        }
    }

    private WorkflowDefinition requireDefinition(Long id) {
        return definitionRepository.findById(id).orElseThrow(() -> notFound("workflow definition"));
    }

    private void requireVersionOwner(Long definitionId, WorkflowVersion version) {
        if (!definitionId.equals(version.definitionId())) {
            throw new BusinessException("B2500", "workflow version belongs to another definition");
        }
    }

    private void validatePublishable(
            WorkflowDefinition definition, WorkflowVersion version, List<WorkflowNode> nodes,
            List<WorkflowTransition> transitions) {
        if (definition.status() == WorkflowDefinition.Status.INACTIVE
                || definition.status() == WorkflowDefinition.Status.ARCHIVED) {
            throw new BusinessException("B2503", "inactive or archived workflow definition cannot publish");
        }
        if (version.status() != WorkflowVersion.Status.DRAFT) {
            throw new BusinessException("B2503", "only DRAFT workflow version can be published");
        }
        if (!"1.0".equals(version.schemaVersion()) && !"2.0".equals(version.schemaVersion())) {
            throw new BusinessException("B2500", "unsupported workflow schema version");
        }
        if (version.engineMode() == WorkflowEngineMode.MULTI_NODE_LINEAR_V1) {
            try {
                graphValidator.validateAndFindEntry(version.id(), nodes, transitions);
                return;
            } catch (IllegalStateException exception) {
                throw invalid(exception);
            }
        }
        if (nodes.size() != 1 || !nodes.getFirst().enabled()
                || nodes.getFirst().nodeType() != WorkflowNode.NodeType.APPROVAL
                || nodes.getFirst().approvalMode() != WorkflowNode.ApprovalMode.SINGLE) {
            throw new BusinessException("B2500", "Workflow Lite publication requires exactly one enabled SINGLE approval node");
        }
        WorkflowNode node = nodes.getFirst();
        if (node.nodeOrder() != 1 || node.assignmentRuleConfig().isBlank()
                || !(node.assignmentRuleConfig().trim().startsWith("{")
                || node.assignmentRuleConfig().trim().startsWith("["))) {
            throw new BusinessException("B2500", "workflow node order or assignment configuration is invalid");
        }
    }

    private WorkflowTransitionRepository requireTransitionRepository() {
        if (transitionRepository == null) {
            throw new BusinessException("B2500", "workflow transition repository is unavailable");
        }
        return transitionRepository;
    }

    private VersionNodeResolverBindingRepository requireBindingRepository() {
        if (bindingRepository == null) {
            throw new BusinessException("B2620", "Version resolver binding repository is unavailable");
        }
        return bindingRepository;
    }

    private ResolverBindingManifestRepository requireManifestRepository() {
        if (manifestRepository == null) {
            throw new BusinessException("B2620", "resolver binding manifest repository is unavailable");
        }
        return manifestRepository;
    }

    private ResolverRegistry requireResolverRegistry() {
        if (resolverRegistry == null) {
            throw new BusinessException("B2620", "resolver registry is unavailable");
        }
        return resolverRegistry;
    }

    private void requireManagementScope(WorkflowDefinition definition, SecurityPrincipal principal) {
        if (principal.allDataScope()) return;
        if (definition.ownerOrgId() == null || principal.allowedOrgIds() == null
                || !principal.allowedOrgIds().contains(definition.ownerOrgId())) {
            throw new BusinessException("B2513", "workflow definition is outside current organization scope");
        }
    }

    private void ensureUniqueNodeKeys(List<ReplaceWorkflowNodesCommand.NodeCommand> nodes) {
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ReplaceWorkflowNodesCommand.NodeCommand node : nodes) {
            if (!codes.add(node.nodeCode()) || !orders.add(node.nodeOrder())) {
                throw new BusinessException("B2509", "node code and order must be unique in a version");
            }
        }
    }

    private BusinessException notFound(String resource) {
        return new BusinessException("B2504", resource + " does not exist");
    }

    private BusinessException invalid(RuntimeException cause) {
        BusinessException exception = new BusinessException("B2500", cause.getMessage());
        exception.initCause(cause);
        return exception;
    }

    private BusinessException concurrentChange(String resource) {
        return new BusinessException("B2529", resource + " was changed concurrently");
    }
}
