package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.vo.*;
import cn.gov.enterprise.modules.workflow.domain.assignment.*;
import cn.gov.enterprise.modules.workflow.domain.model.*;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.service.WorkflowResolverBindingHasher;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes and reads instance/node resolver facts. No mutation or rerouting API exists. */
@Service
public class WorkflowResolverBindingApplicationService {
    private final ResolverBindingSetRepository setRepository;
    private final ResolverBindingRepository resolverRepository;
    private final NodeResolverBindingRepository nodeRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowIdentityGenerator identityGenerator;
    private final WorkflowAssignmentResolverApplicationService resolverService;
    private final CurrentSecurityContext securityContext;
    private final WorkflowResolverBindingHasher hasher = new WorkflowResolverBindingHasher();

    public record FrozenBindings(WorkflowResolverBindingSet set,
                                 List<WorkflowResolverBinding> resolvers,
                                 List<NodeResolverBinding> nodes) {
        public FrozenBindings { resolvers = List.copyOf(resolvers); nodes = List.copyOf(nodes); }
        public NodeResolverBinding requireNode(Long nodeId) {
            return nodes.stream().filter(item -> item.nodeId().equals(nodeId)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("frozen node resolver binding is missing"));
        }
        public WorkflowResolverBinding requireResolver(Long id) {
            return resolvers.stream().filter(item -> item.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("frozen resolver binding is missing"));
        }
    }

    public WorkflowResolverBindingApplicationService(
            ResolverBindingSetRepository setRepository, ResolverBindingRepository resolverRepository,
            NodeResolverBindingRepository nodeRepository, WorkflowInstanceRepository instanceRepository,
            WorkflowIdentityGenerator identityGenerator,
            WorkflowAssignmentResolverApplicationService resolverService,
            CurrentSecurityContext securityContext) {
        this.setRepository = setRepository; this.resolverRepository = resolverRepository;
        this.nodeRepository = nodeRepository; this.instanceRepository = instanceRepository;
        this.identityGenerator = identityGenerator; this.resolverService = resolverService;
        this.securityContext = securityContext;
    }

    /** Builds all frozen facts before the first write; current runtime intentionally supports USER+DIRECT only. */
    public FrozenBindings prepare(WorkflowInstance instance, WorkflowDefinition definition,
                                  WorkflowVersion workflowVersion, List<WorkflowNode> nodes,
                                  LocalDateTime frozenTime, String auditInfo) {
        if (nodes == null || nodes.isEmpty()) throw new BusinessException("B2650", "workflow has no nodes to bind");
        ResolverVersionBinding legacy = resolverService.freeze(instance.id(), ExplicitUserResolver.CODE,
                ExplicitUserResolver.RESOLVER_VERSION, ExplicitUserResolver.CONTRACT_HASH);
        AssignmentResolver descriptorSource = resolverService.select(legacy);
        List<NodePlan> plans = nodes.stream().filter(WorkflowNode::enabled)
                .sorted(Comparator.comparing(WorkflowNode::nodeCode)).map(node -> plan(node, descriptorSource)).toList();
        String aggregateRuleHash = digest(plans.stream().map(NodePlan::ruleHash).sorted().toList());
        Long setId = identityGenerator.nextId();
        Long resolverId = identityGenerator.nextId();
        WorkflowResolverBinding resolver = new WorkflowResolverBinding(resolverId, setId, instance.id(), workflowVersion.id(),
                descriptorSource.descriptor().code(), descriptorSource.descriptor().version(),
                descriptorSource.descriptor().strategyType(), descriptorSource.descriptor().mode(),
                descriptorSource.descriptor().contractHash(), aggregateRuleHash,
                WorkflowResolverBindingSet.Status.FROZEN, frozenTime, auditInfo, 0);
        List<NodeResolverBinding> bindings = plans.stream().map(plan -> new NodeResolverBinding(
                identityGenerator.nextId(), setId, resolverId, instance.id(), workflowVersion.id(),
                plan.node().id(), plan.node().nodeCode(), plan.strategyType(), plan.mode(), plan.targetType(),
                plan.targetSnapshot(), WorkflowResolverBindingHasher.RULE_VERSION, plan.ruleSnapshot(),
                plan.ruleHash(), hasher.nodeHash(plan.node().nodeCode(), resolver,
                plan.targetType().name(), plan.targetSnapshot(), plan.ruleHash()),
                WorkflowResolverBindingSet.Status.FROZEN, frozenTime, auditInfo, 0)).toList();
        String manifest = hasher.manifestHash(bindings);
        WorkflowResolverBindingSet set = new WorkflowResolverBindingSet(setId, instance.id(), definition.id(),
                workflowVersion.id(), WorkflowResolverBindingHasher.MANIFEST_VERSION, manifest,
                bindings.size(), WorkflowResolverBindingSet.Status.FROZEN, frozenTime, auditInfo, 0);
        return new FrozenBindings(set, List.of(resolver), bindings);
    }

    public void save(FrozenBindings frozen) {
        setRepository.save(frozen.set());
        resolverRepository.saveAll(frozen.resolvers());
        nodeRepository.saveAll(frozen.nodes());
    }

    public Optional<NodeResolverBinding> findNode(Long instanceId, Long nodeId) {
        return nodeRepository.findByInstanceIdAndNodeId(instanceId, nodeId);
    }

    public Optional<WorkflowResolverBindingSet> querySetInternal(Long instanceId) {
        return setRepository.findByInstanceId(instanceId);
    }

    public AssignmentResolver runtimeResolver(NodeResolverBinding node) {
        WorkflowResolverBinding binding = resolverRepository.findById(node.resolverBindingId())
                .orElseThrow(() -> new BusinessException("B2654", "frozen resolver binding does not exist"));
        if (binding.status() == WorkflowResolverBindingSet.Status.SECURITY_BLOCKED) {
            throw new BusinessException("B2653", "frozen resolver binding is security blocked");
        }
        return resolverService.selectForExistingInstance(binding.asLegacyBinding());
    }

    public AssignmentResolver runtimeResolver(WorkflowResolverBinding binding) {
        if (binding.status() == WorkflowResolverBindingSet.Status.SECURITY_BLOCKED) {
            throw new BusinessException("B2653", "frozen resolver binding is security blocked");
        }
        return resolverService.selectForExistingInstance(binding.asLegacyBinding());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowResolverBindingSetDetail querySet(Long instanceId) {
        WorkflowInstance instance = requireAccessible(instanceId);
        return setRepository.findByInstanceId(instanceId)
                .map(set -> WorkflowResolverBindingSetDetail.frozen(set,
                        resolverRepository.findByInstanceId(instanceId), nodeRepository.findByInstanceId(instanceId)))
                .orElseGet(() -> WorkflowResolverBindingSetDetail.legacy(instance.resolverVersionBinding() != null));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('workflow:view')")
    public WorkflowNodeResolverBindingDetail queryNode(Long instanceId, Long nodeId) {
        WorkflowInstance instance = requireAccessible(instanceId);
        return nodeRepository.findByInstanceIdAndNodeId(instanceId, nodeId).map(node -> {
            WorkflowResolverBinding resolver = resolverRepository.findById(node.resolverBindingId())
                    .orElseThrow(() -> new BusinessException("B2654", "frozen resolver binding does not exist"));
            return WorkflowNodeResolverBindingDetail.frozen(node, resolver);
        }).orElseGet(() -> {
            if (setRepository.findByInstanceId(instanceId).isPresent()) {
                throw new BusinessException("B2654", "multi-resolver instance has a missing node binding");
            }
            return WorkflowNodeResolverBindingDetail.legacy();
        });
    }

    private WorkflowInstance requireAccessible(Long instanceId) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException("B2654", "workflow instance does not exist"));
        SecurityPrincipal principal = securityContext.principal();
        boolean accessible = principal.allDataScope() || instance.initiatorUserId().equals(principal.userId())
                || (principal.allowedOrgIds() != null && principal.allowedOrgIds().contains(instance.initiatorOrgId()));
        if (!accessible) throw new BusinessException("B2653", "resolver binding is outside current data scope");
        return instance;
    }

    private NodePlan plan(WorkflowNode node, AssignmentResolver resolver) {
        if (node.assignmentRuleType() != WorkflowNode.AssignmentRuleType.USER
                || resolver.descriptor().strategyType() != AssignmentStrategy.Type.USER
                || resolver.descriptor().mode() != ResolverMode.DIRECT) {
            throw new BusinessException("B2650", "only USER + DIRECT resolver binding is enabled");
        }
        Long userId = new cn.gov.enterprise.modules.workflow.domain.service.ExplicitUserAssignment().resolve(node);
        String target = "{\"userId\":" + userId + "}";
        String rule = node.assignmentRuleConfig().trim();
        String hash = hasher.ruleHash("USER", "DIRECT", "USER", target, rule);
        return new NodePlan(node, AssignmentStrategy.Type.USER, ResolverMode.DIRECT,
                AssignmentStrategy.Type.USER, target, rule, hash);
    }

    private String digest(List<String> values) {
        try {
            String canonical = values.stream().map(v -> v.length() + ":" + v + "|")
                    .reduce("", String::concat);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private record NodePlan(WorkflowNode node, AssignmentStrategy.Type strategyType,
                            ResolverMode mode, AssignmentStrategy.Type targetType,
                            String targetSnapshot, String ruleSnapshot, String ruleHash) { }
}
