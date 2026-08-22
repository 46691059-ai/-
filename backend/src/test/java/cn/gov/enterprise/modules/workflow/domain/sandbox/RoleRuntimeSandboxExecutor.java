package cn.gov.enterprise.modules.workflow.domain.sandbox;

import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import cn.gov.enterprise.modules.workflow.domain.role.CandidateResolutionMode;
import cn.gov.enterprise.modules.workflow.domain.role.ResolverBindingProposalSet;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProposal;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingProvider;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverBindingRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleResolverContext;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivation;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.ApproverRole;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.Decision;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationGate;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibility;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibilityRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEligibilityValidator;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeBindingCandidate;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed-loop, in-memory ROLE execution laboratory. It has no repository,
 * WorkflowInstance, Task, production CandidatePool, or Claim dependency.
 */
public final class RoleRuntimeSandboxExecutor {
    private final FakeRoleDirectoryAdapter directory;

    public RoleRuntimeSandboxExecutor(FakeRoleDirectoryAdapter directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public SandboxExecutionResult execute(SandboxRoleRuntimeContext context) {
        return execute(context, SandboxFault.NONE);
    }

    public SandboxExecutionResult execute(
            SandboxRoleRuntimeContext context, SandboxFault fault) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(fault, "fault");
        Map<String, String> audit = new LinkedHashMap<>();
        SandboxExecutionTrace trace = new SandboxExecutionTrace();
        try {
            ResolverRegistry registry = sandboxRegistry();
            ResolverContractHash boundContract = fault == SandboxFault.CONTRACT_HASH_DRIFT
                    ? ResolverContractHash.of("0".repeat(64)) : context.contractHash();
            ResolverVersionBinding syntheticBinding = new ResolverVersionBinding(
                    9_000_001L, RoleDirectoryResolver.CODE,
                    context.resolverVersion(), boundContract);

            RoleResolverContext resolverContext = new RoleResolverContext(
                    syntheticBinding.instanceId(), 9_000_002L, syntheticBinding,
                    context.roleCode(), context.organizationId(),
                    "SANDBOX_ENTERPRISE_FIXED", context.effectiveAt(), context.sandboxId());
            RoleDirectoryResult directoryResult = new RoleDirectoryResolver(directory)
                    .resolveDirectory(resolverContext);
            audit.put("DIRECTORY_RESOLVED", directoryResult.resultHash());
            trace.advance(SandboxExecutionStatus.DIRECTORY_RESOLVED);

            RoleCandidateResult candidates = new RoleCandidateAdapter(context.candidateLimit())
                    .adapt(new RoleCandidateContext(syntheticBinding.instanceId(), 9_000_002L,
                            context.roleCode(), context.organizationId(), context.effectiveAt(),
                            new ResolverVersionBinding(syntheticBinding.instanceId(),
                                    RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                                    RoleDirectoryResolver.CONTRACT_HASH)), directoryResult);
            audit.put("CANDIDATE_ADAPTED", candidates.candidateHash());

            ResolverBindingProposalSet set = new RoleResolverBindingProvider(registry).propose(
                    9_000_003L, 9_000_004L,
                    List.of(new RoleResolverBindingRequest(9_000_005L,
                            context.roleCode(), context.organizationId(), context.effectiveAt())));
            RoleResolverBindingProposal proposal = set.proposals().getFirst();
            if (fault == SandboxFault.BINDING_HASH_DRIFT) {
                set = new ResolverBindingProposalSet(set.definitionId(), set.versionId(),
                        set.proposals(), "0".repeat(64));
            }
            audit.put("BINDING_GENERATED", set.bindingHash());
            trace.advance(SandboxExecutionStatus.BINDING_GENERATED);

            RoleRuntimeEligibilityValidator eligibilityValidator =
                    new RoleRuntimeEligibilityValidator(new RuntimeEligibilityRegistry(
                            registry, Set.of()));
            RoleRuntimeEligibility eligibility = eligibilityValidator.validate(
                    new RoleRuntimeEligibilityRequest(set, proposal, candidates,
                            context.candidateLimit()));
            if (!eligibility.ready()) {
                throw new IllegalStateException(eligibility.reason());
            }
            String runtimeHash = eligibilityValidator.runtimeHash(
                    eligibility, candidates, context.candidateLimit());
            audit.put("ELIGIBILITY_PASSED", runtimeHash);
            trace.advance(SandboxExecutionStatus.ELIGIBILITY_PASSED);

            RoleRuntimeActivationRequest activationRequest = new RoleRuntimeActivationRequest(
                    proposal.resolverCode(), proposal.resolverVersion(), proposal.contractHash(),
                    set.bindingHash(), candidates.candidateHash(), directoryResult.resultHash(),
                    context.effectiveAt(), "ROLE_RUNTIME_SANDBOX", "SANDBOX_REQUESTER");
            RoleRuntimeActivationEvidence activationEvidence = new RoleRuntimeActivationEvidence(
                    proposal.contractHash().value(), set.bindingHash(), candidates.candidateHash(),
                    directoryResult.resultHash(), "ROLE_RUNTIME_SANDBOX", context.effectiveAt(),
                    true, true, true);
            RoleRuntimeActivation activation = RoleRuntimeActivation.draft(activationRequest)
                    .evaluate(new RoleRuntimeActivationGate(
                            new RuntimeEligibilityRegistry(registry, Set.of())), activationEvidence);
            if (fault == SandboxFault.ACTIVATION_NOT_APPROVED) {
                throw new IllegalStateException("ACTIVATION_APPROVAL_MISSING");
            }
            activation = approve(activation, context, ApproverRole.BUSINESS_OWNER, "BUSINESS");
            activation = approve(activation, context, ApproverRole.SECURITY_AUDIT, "SECURITY");
            activation = approve(activation, context, ApproverRole.RELEASE_APPROVER, "RELEASE");
            RoleRuntimeActivationSnapshot activationSnapshot = activation.snapshot();
            audit.put("ACTIVATION_APPROVED", activationSnapshot.evidenceHash());
            trace.advance(SandboxExecutionStatus.ACTIVATION_APPROVED);

            RuntimeBindingCandidate runtimeCandidate = RuntimeBindingCandidate.from(
                    eligibility, context.effectiveAt());
            audit.put("RUNTIME_BINDING_CANDIDATE", SandboxHash.sha256(
                    runtimeCandidate.proposalHash() + '|' + runtimeCandidate.eligibilityStatus()
                            + '|' + runtimeCandidate.generatedAt()));
            audit.put("CANDIDATE_GENERATED", candidates.candidateHash());
            trace.advance(SandboxExecutionStatus.CANDIDATE_GENERATED);

            String syntheticUser = candidates.candidateUsers().getFirst().userId();
            String claimHash = SandboxHash.sha256(context.sandboxId() + '|'
                    + syntheticUser + '|' + candidates.candidateHash() + "|CLAIM_SIMULATION_V1");
            SandboxClaimSimulation claim = new SandboxClaimSimulation(context.sandboxId(),
                    syntheticUser, candidates.candidateHash(), claimHash);
            audit.put("CLAIM_SIMULATED", claimHash);
            trace.advance(SandboxExecutionStatus.COMPLETED);

            return new SandboxExecutionResult(set.bindingHash(), candidates.candidateHash(),
                    activation.activationHash(), trace.status(),
                    new SandboxAuditEvidence(audit), claim, "");
        } catch (RuntimeException exception) {
            trace.advance(SandboxExecutionStatus.FAILED);
            audit.put("FAILED", SandboxHash.sha256(exception.getClass().getName() + '|'
                    + Objects.toString(exception.getMessage(), "NO_MESSAGE")));
            return new SandboxExecutionResult(null, null, null, SandboxExecutionStatus.FAILED,
                    new SandboxAuditEvidence(audit), null,
                    Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }

    private RoleRuntimeActivation approve(
            RoleRuntimeActivation activation, SandboxRoleRuntimeContext context,
            ApproverRole role, String evidence) {
        return activation.decide(new RoleRuntimeActivationDecision(Decision.APPROVE, role,
                "SANDBOX_" + role.name(), "Sandbox approval evidence",
                context.effectiveAt().plusSeconds(role.ordinal()),
                SandboxHash.sha256(context.sandboxId() + '|' + role + '|' + evidence)));
    }

    private ResolverRegistry sandboxRegistry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                RoleCandidateAdapter.PREPARED_DESCRIPTOR), List.of(explicit));
    }
}
