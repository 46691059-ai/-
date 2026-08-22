package cn.gov.enterprise.modules.workflow.domain.role.admission;

import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.AuditCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.CanaryScopePort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.DataScopeCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.DirectoryCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.FeatureFlagCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.KillSwitchCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.RealtimeEligibilityCapabilityPort;
import static cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.SoDCapabilityPort;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Executes the frozen 28 checks in sequence and stops at the first failure. */
public final class RoleRuntimeExecutionAdmissionValidator {
    private final ResolverRegistry resolverRegistry;
    private final DirectoryCapabilityPort directory;
    private final RealtimeEligibilityCapabilityPort realtimeEligibility;
    private final DataScopeCapabilityPort dataScope;
    private final SoDCapabilityPort sod;
    private final AuditCapabilityPort audit;
    private final FeatureFlagCapabilityPort featureFlag;
    private final KillSwitchCapabilityPort killSwitch;
    private final CanaryScopePort canary;

    public RoleRuntimeExecutionAdmissionValidator(
            ResolverRegistry resolverRegistry,
            DirectoryCapabilityPort directory,
            RealtimeEligibilityCapabilityPort realtimeEligibility,
            DataScopeCapabilityPort dataScope,
            SoDCapabilityPort sod,
            AuditCapabilityPort audit,
            FeatureFlagCapabilityPort featureFlag,
            KillSwitchCapabilityPort killSwitch,
            CanaryScopePort canary) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.realtimeEligibility = Objects.requireNonNull(
                realtimeEligibility, "realtimeEligibility");
        this.dataScope = Objects.requireNonNull(dataScope, "dataScope");
        this.sod = Objects.requireNonNull(sod, "sod");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.featureFlag = Objects.requireNonNull(featureFlag, "featureFlag");
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch");
        this.canary = Objects.requireNonNull(canary, "canary");
    }

    RoleRuntimeExecutionAdmissionValidation validate(
            RoleRuntimeExecutionAdmissionRequest request,
            RoleRuntimeExecutionAdmissionFacts facts,
            Instant validatedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(validatedAt, "validatedAt");
        Chain chain = new Chain();
        if (!chain.pass(1, "PERSISTED_CANDIDATE_EXISTS", facts != null && facts.candidateExists(),
                RoleRuntimeExecutionBlockReason.CANDIDATE_NOT_FOUND)) return chain.blocked();
        if (!chain.pass(2, "CANDIDATE_SNAPSHOT_STATUS",
                facts.candidateStatus() == RoleRuntimeBindingLifecycleStatus.ACTIVE,
                RoleRuntimeExecutionBlockReason.CANDIDATE_SNAPSHOT_STATUS_INVALID)) {
            return chain.blocked();
        }
        if (!chain.pass(3, "PROMOTION_EVIDENCE_COMPLETE", facts.promotionEvidenceComplete(),
                RoleRuntimeExecutionBlockReason.PROMOTION_EVIDENCE_MISSING)) return chain.blocked();
        if (!chain.pass(4, "ACTIVATION_EVIDENCE_COMPLETE", facts.activationEvidenceComplete(),
                RoleRuntimeExecutionBlockReason.ACTIVATION_EVIDENCE_MISSING)) return chain.blocked();
        if (!chain.pass(5, "ACTIVATION_NOT_REVOKED", !facts.activationRevoked(),
                RoleRuntimeExecutionBlockReason.ACTIVATION_REVOKED)) return chain.blocked();
        if (!chain.pass(6, "PROMOTION_NOT_REVOKED", !facts.promotionRevoked(),
                RoleRuntimeExecutionBlockReason.PROMOTION_REVOKED)) return chain.blocked();
        if (!chain.pass(7, "RESOLVER_CODE", request.resolverCode().equals(facts.resolverCode()),
                RoleRuntimeExecutionBlockReason.RESOLVER_CODE_MISMATCH)) return chain.blocked();
        if (!chain.pass(8, "RESOLVER_VERSION",
                request.resolverVersion().equals(facts.resolverVersion()),
                RoleRuntimeExecutionBlockReason.RESOLVER_VERSION_MISMATCH)) return chain.blocked();
        if (!chain.pass(9, "RESOLVER_CONTRACT_HASH",
                request.resolverContractHash().equals(facts.resolverContractHash()),
                RoleRuntimeExecutionBlockReason.RESOLVER_CONTRACT_HASH_MISMATCH)) {
            return chain.blocked();
        }
        if (!chain.pass(10, "ACTIVATION_HASH",
                request.activationHash().equals(facts.activationHash()),
                RoleRuntimeExecutionBlockReason.ACTIVATION_HASH_MISMATCH)) return chain.blocked();
        if (!chain.pass(11, "PROMOTION_HASH",
                request.promotionHash().equals(facts.promotionHash()),
                RoleRuntimeExecutionBlockReason.PROMOTION_HASH_MISMATCH)) return chain.blocked();
        if (!chain.pass(12, "BINDING_HASH", request.bindingHash().equals(facts.bindingHash()),
                RoleRuntimeExecutionBlockReason.BINDING_HASH_MISMATCH)) return chain.blocked();
        if (!chain.pass(13, "CANDIDATE_HASH",
                request.candidateHash().equals(facts.candidateHash()),
                RoleRuntimeExecutionBlockReason.CANDIDATE_HASH_MISMATCH)) return chain.blocked();
        if (request.directoryRevision() != facts.directoryRevision()) {
            chain.pass(14, "DIRECTORY_REVISION_AND_RESULT_HASH", false,
                    RoleRuntimeExecutionBlockReason.DIRECTORY_REVISION_MISMATCH);
            return chain.blocked();
        }
        if (!request.directoryResultHash().equals(facts.directoryResultHash())) {
            chain.pass(14, "DIRECTORY_REVISION_AND_RESULT_HASH", false,
                    RoleRuntimeExecutionBlockReason.DIRECTORY_RESULT_HASH_MISMATCH);
            return chain.blocked();
        }
        chain.pass(14, "DIRECTORY_REVISION_AND_RESULT_HASH", true,
                RoleRuntimeExecutionBlockReason.NONE);
        boolean effective = request.effectiveAt().equals(facts.effectiveAt())
                && validatedAt.isBefore(facts.effectiveUntil());
        if (!chain.pass(15, "EFFECTIVE_AT", effective,
                RoleRuntimeExecutionBlockReason.EFFECTIVE_AT_INVALID)) return chain.blocked();
        if (!chain.pass(16, "BUSINESS_SCOPE",
                request.businessScope().equals(facts.businessScope()),
                RoleRuntimeExecutionBlockReason.BUSINESS_SCOPE_MISMATCH)) return chain.blocked();
        boolean definitionMatches = request.definitionId().equals(facts.definitionId())
                && request.definitionVersionId().equals(facts.definitionVersionId());
        if (!chain.pass(17, "DEFINITION_VERSION", definitionMatches,
                RoleRuntimeExecutionBlockReason.DEFINITION_VERSION_MISMATCH)) return chain.blocked();
        if (!chain.pass(18, "NODE_BINDING", request.nodeId().equals(facts.nodeId()),
                RoleRuntimeExecutionBlockReason.NODE_BINDING_MISMATCH)) return chain.blocked();

        AssignmentResolverDescriptor descriptor;
        try {
            descriptor = resolverRegistry.requireDescriptor(
                    ResolverCode.of(request.resolverCode()),
                    ResolverVersion.of(request.resolverVersion()));
            chain.pass(19, "RESOLVER_DESCRIPTOR", true, RoleRuntimeExecutionBlockReason.NONE);
        } catch (RuntimeException exception) {
            chain.pass(19, "RESOLVER_DESCRIPTOR", false,
                    RoleRuntimeExecutionBlockReason.RESOLVER_DESCRIPTOR_NOT_FOUND);
            return chain.blocked();
        }
        boolean admissionMetadataOnly = descriptor.status() == ResolverStatus.PREPARED
                && !descriptor.enabled()
                && descriptor.contractHash().value().equals(request.resolverContractHash());
        if (!chain.pass(20, "RESOLVER_ADMISSION_STATUS", admissionMetadataOnly,
                RoleRuntimeExecutionBlockReason.RESOLVER_STATUS_NOT_ADMISSION_ELIGIBLE)) {
            return chain.blocked();
        }

        if (!capability(chain, 21, "DIRECTORY", directory,
                request, RoleRuntimeExecutionBlockReason.DIRECTORY_CAPABILITY_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 22, "REALTIME_ELIGIBILITY", realtimeEligibility,
                request, RoleRuntimeExecutionBlockReason.REALTIME_ELIGIBILITY_CAPABILITY_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 23, "DATA_SCOPE", dataScope,
                request, RoleRuntimeExecutionBlockReason.DATA_SCOPE_CAPABILITY_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 24, "SOD", sod,
                request, RoleRuntimeExecutionBlockReason.SOD_CAPABILITY_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 25, "AUDIT", audit,
                request, RoleRuntimeExecutionBlockReason.AUDIT_CAPABILITY_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 26, "FEATURE_FLAG", featureFlag,
                request, RoleRuntimeExecutionBlockReason.FEATURE_FLAG_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 27, "KILL_SWITCH", killSwitch,
                request, RoleRuntimeExecutionBlockReason.KILL_SWITCH_NOT_READY)) {
            return chain.blocked();
        }
        if (!capability(chain, 28, "CANARY_SCOPE", canary,
                request, RoleRuntimeExecutionBlockReason.CANARY_SCOPE_NOT_ALLOWED)) {
            return chain.blocked();
        }
        return chain.eligible();
    }

    private boolean capability(Chain chain, int sequence, String name, Object port,
            RoleRuntimeExecutionAdmissionRequest request,
            RoleRuntimeExecutionBlockReason reason) {
        Supplier<RoleRuntimeCapabilityResult> call = switch (port) {
            case DirectoryCapabilityPort value -> () -> value.assess(request);
            case RealtimeEligibilityCapabilityPort value -> () -> value.assess(request);
            case DataScopeCapabilityPort value -> () -> value.assess(request);
            case SoDCapabilityPort value -> () -> value.assess(request);
            case AuditCapabilityPort value -> () -> value.assess(request);
            case FeatureFlagCapabilityPort value -> () -> value.assess(request);
            case KillSwitchCapabilityPort value -> () -> value.assess(request);
            case CanaryScopePort value -> () -> value.assess(request);
            default -> throw new IllegalArgumentException("unsupported capability port");
        };
        RoleRuntimeCapabilityResult result;
        try {
            result = call.get();
            if (result == null) {
                result = new RoleRuntimeCapabilityResult(
                        name, RoleRuntimeCapabilityReadiness.BLOCKED, "NULL_CAPABILITY_RESULT");
            }
        } catch (RuntimeException exception) {
            result = new RoleRuntimeCapabilityResult(
                    name, RoleRuntimeCapabilityReadiness.BLOCKED,
                    "CAPABILITY_EXCEPTION:" + exception.getClass().getSimpleName());
        }
        chain.capabilities.put(name, result);
        return chain.pass(sequence, name + "_READY", result.ready(), reason);
    }

    private static final class Chain {
        private final List<RoleRuntimeExecutionAdmissionCheck> checks = new ArrayList<>();
        private final Map<String, RoleRuntimeCapabilityResult> capabilities = new LinkedHashMap<>();
        private RoleRuntimeExecutionBlockReason failure = RoleRuntimeExecutionBlockReason.NONE;

        boolean pass(int sequence, String code, boolean passed,
                RoleRuntimeExecutionBlockReason reason) {
            RoleRuntimeExecutionBlockReason actual = passed
                    ? RoleRuntimeExecutionBlockReason.NONE : reason;
            checks.add(new RoleRuntimeExecutionAdmissionCheck(sequence, code, passed, actual));
            if (!passed) failure = actual;
            return passed;
        }

        RoleRuntimeExecutionAdmissionValidation blocked() {
            return new RoleRuntimeExecutionAdmissionValidation(
                    false, failure, checks, capabilities);
        }

        RoleRuntimeExecutionAdmissionValidation eligible() {
            return new RoleRuntimeExecutionAdmissionValidation(true,
                    RoleRuntimeExecutionBlockReason.NONE, checks, capabilities);
        }
    }
}
