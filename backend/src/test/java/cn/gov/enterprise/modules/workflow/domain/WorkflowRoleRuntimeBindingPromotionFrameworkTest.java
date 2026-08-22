package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.application.service.RuntimeBindingPromotionApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.repository.RuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceType;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingCandidate;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionAudit;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionRequest;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionStatus;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.InMemoryRuntimeBindingPromotionRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeBindingPromotionFrameworkTest {
    private static final Instant EFFECTIVE = Instant.parse("2026-08-13T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final String ACTIVATION = "a".repeat(64);
    private static final String BINDING = "b".repeat(64);
    private static final String CANDIDATE = "c".repeat(64);
    private static final String DIRECTORY = "d".repeat(64);
    private static final String PERMISSION = "e".repeat(64);
    private static final String APPROVAL = "f".repeat(64);

    @Test
    void approvedActivationEvidenceMustPromoteOnlyToNonExecutableCandidate() {
        RuntimeBindingPromotionDecision decision = service().promote(request(), trail(), NOW);

        assertThat(decision.status()).isEqualTo(RuntimeBindingPromotionStatus.PROMOTED);
        assertThat(decision.audit().entries()).extracting(RuntimeBindingPromotionAudit.Entry::status)
                .containsExactly(RuntimeBindingPromotionStatus.CREATED,
                        RuntimeBindingPromotionStatus.VALIDATING,
                        RuntimeBindingPromotionStatus.ELIGIBLE,
                        RuntimeBindingPromotionStatus.APPROVED,
                        RuntimeBindingPromotionStatus.PROMOTED);
        RuntimeBindingCandidate candidate = decision.candidateOptional().orElseThrow();
        assertThat(candidate.runtimeEnabled()).isFalse();
        assertThat(candidate.promotionHash()).matches("[0-9a-f]{64}");
        assertThat(candidate.resolverCode()).isEqualTo(RoleDirectoryResolver.CODE);
    }

    @Test
    void missingActivationAndUnapprovedStatusMustFailClosed() {
        RuntimeBindingPromotionDecision missing = policy().promote(request(), null, NOW);
        assertThat(missing.status()).isEqualTo(RuntimeBindingPromotionStatus.BLOCKED);
        assertThat(missing.reason()).isEqualTo("ACTIVATION_EVIDENCE_MISSING");

        RuntimeBindingPromotionEvidence base = RuntimeBindingPromotionEvidence.from(trail());
        RuntimeBindingPromotionEvidence unapproved = evidence(base, PersistentActivationStatus.APPROVED,
                base.activationHash(), base.resolverContractHash(), base.bindingHash(),
                base.candidateHash(), base.directoryRevision());
        RuntimeBindingPromotionDecision blocked = policy().promote(request(), unapproved, NOW);
        assertThat(blocked.status()).isEqualTo(RuntimeBindingPromotionStatus.BLOCKED);
        assertThat(blocked.reason()).isEqualTo("ACTIVATION_NOT_APPROVED");
    }

    @Test
    void activationContractBindingAndCandidateHashDriftMustBeRejectedInOrder() {
        RuntimeBindingPromotionEvidence base = RuntimeBindingPromotionEvidence.from(trail());
        assertBlocked(evidence(base, base.activationStatus(), "0".repeat(64),
                base.resolverContractHash(), base.bindingHash(), base.candidateHash(), 17),
                "ACTIVATION_HASH_MISMATCH");
        assertBlocked(evidence(base, base.activationStatus(), base.activationHash(),
                "0".repeat(64), base.bindingHash(), base.candidateHash(), 17),
                "RESOLVER_CONTRACT_HASH_MISMATCH");
        assertBlocked(evidence(base, base.activationStatus(), base.activationHash(),
                base.resolverContractHash(), "0".repeat(64), base.candidateHash(), 17),
                "BINDING_HASH_MISMATCH");
        assertBlocked(evidence(base, base.activationStatus(), base.activationHash(),
                base.resolverContractHash(), base.bindingHash(), "0".repeat(64), 17),
                "CANDIDATE_HASH_MISMATCH");
    }

    @Test
    void directoryRevisionTimeAndPermissionMustBeCheckedAfterHashes() {
        RuntimeBindingPromotionEvidence base = RuntimeBindingPromotionEvidence.from(trail());
        assertBlocked(evidence(base, base.activationStatus(), base.activationHash(),
                base.resolverContractHash(), base.bindingHash(), base.candidateHash(), 18),
                "DIRECTORY_REVISION_MISMATCH");

        RuntimeBindingPromotionRequest expired = request(NOW.minusSeconds(3600), NOW, true);
        assertThat(policy().promote(expired, base, NOW).reason()).isEqualTo("EFFECTIVE_TIME_INVALID");
        RuntimeBindingPromotionRequest denied = request(EFFECTIVE, NOW.plusSeconds(3600), false);
        assertThat(policy().promote(denied, base, NOW).reason()).isEqualTo("PROMOTION_PERMISSION_DENIED");
    }

    @Test
    void duplicatePromotionMustBeRejectedByInsertOnlyRepository() {
        RuntimeBindingPromotionApplicationService service = service();
        service.promote(request(), trail(), NOW);
        assertThatThrownBy(() -> service.promote(request(), trail(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate runtime binding promotion");
    }

    @Test
    void promotionMustNotCreateRuntimeObjectsOrChangeResolverRegistry() {
        ResolverRegistry registry = resolverRegistry();
        RuntimeBindingPromotionDecision decision = policy(registry).promote(
                request(), RuntimeBindingPromotionEvidence.from(trail()), NOW);

        assertThat(decision.candidateOptional()).isPresent();
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        assertThatThrownBy(() -> registry.require(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH))
                .isInstanceOf(RuntimeException.class);
        assertThat(Arrays.stream(RuntimeBindingPromotionDecision.class.getDeclaredFields())
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.contains("WorkflowInstance")
                        || name.contains("WorkflowTask")
                        || name.contains("CandidatePool")
                        || name.contains("Claim"));
    }

    @Test
    void explicitUserAndLegacyPathsMustRemainIsolated() {
        ResolverRegistry registry = resolverRegistry();
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
        assertThat(new InMemoryRuntimeBindingPromotionRepository()
                .findByPromotionId("LEGACY-TASK-1")).isEmpty();
    }

    @Test
    void promotionDomainMustRemainFrameworkMapperAndEntityFree() {
        List<Class<?>> types = List.of(RuntimeBindingPromotionRequest.class,
                RuntimeBindingPromotionDecision.class, RuntimeBindingCandidate.class,
                RuntimeBindingPromotionEvidence.class, RuntimeBindingPromotionPolicy.class,
                RuntimeBindingPromotionAudit.class, RuntimeBindingPromotionCanonical.class);
        assertThat(types.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.startsWith("org.springframework")
                        || name.startsWith("com.baomidou") || name.contains(".entity"));
    }

    private static void assertBlocked(RuntimeBindingPromotionEvidence evidence, String reason) {
        RuntimeBindingPromotionDecision decision = policy().promote(request(), evidence, NOW);
        assertThat(decision.status()).isEqualTo(RuntimeBindingPromotionStatus.BLOCKED);
        assertThat(decision.reason()).isEqualTo(reason);
        assertThat(decision.candidateOptional()).isEmpty();
    }

    private static RuntimeBindingPromotionEvidence evidence(RuntimeBindingPromotionEvidence base,
            PersistentActivationStatus status, String activationHash, String contractHash,
            String bindingHash, String candidateHash, long revision) {
        String promotionEvidenceHash = RuntimeBindingPromotionCanonical.evidenceHash(
                base.activationId(), status.name(), activationHash, base.approvalEvidenceHash(),
                base.auditHash(), base.resolverCode(), base.resolverVersion(), contractHash,
                bindingHash, candidateHash, base.directoryContractHash(), revision,
                base.businessScope(), base.effectiveAt().toString());
        return new RuntimeBindingPromotionEvidence(base.activationId(), status, activationHash,
                base.approvalEvidenceHash(), base.resolverCode(), base.resolverVersion(), contractHash,
                bindingHash, candidateHash, base.directoryContractHash(), revision,
                base.businessScope(), base.effectiveAt(), base.auditHash(), promotionEvidenceHash);
    }

    private static RuntimeBindingPromotionApplicationService service() {
        RuntimeBindingPromotionRepository repository = new InMemoryRuntimeBindingPromotionRepository();
        return new RuntimeBindingPromotionApplicationService(policy(), repository);
    }

    private static RuntimeBindingPromotionPolicy policy() {
        return policy(resolverRegistry());
    }

    private static RuntimeBindingPromotionPolicy policy(ResolverRegistry registry) {
        return new RuntimeBindingPromotionPolicy(new RuntimeEligibilityRegistry(registry, Set.of()));
    }

    private static ResolverRegistry resolverRegistry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RuntimeBindingPromotionRequest request() {
        return request(EFFECTIVE, NOW.plusSeconds(3600), true);
    }

    private static RuntimeBindingPromotionRequest request(
            Instant from, Instant until, boolean permission) {
        return new RuntimeBindingPromotionRequest("PROMOTION-001", "ACT-001", ACTIVATION,
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                RoleDirectoryResolver.CONTRACT_HASH, BINDING, CANDIDATE, 17,
                "INVESTMENT_DECISION", from, until, "release-approver",
                permission, PERMISSION);
    }

    private static ActivationAuditTrail trail() {
        PersistentActivationRequest request = new PersistentActivationRequest(1L, "ACT-001",
                RoleDirectoryResolver.CODE.value(), RoleDirectoryResolver.VERSION.value(),
                RoleDirectoryResolver.CONTRACT_HASH.value(), BINDING, CANDIDATE, DIRECTORY,
                17, ACTIVATION, APPROVAL, "INVESTMENT_DECISION", EFFECTIVE,
                "requester", PersistentActivationStatus.PERSISTED, 0);
        List<PersistentActivationDecision> decisions = List.of(
                decision(11L, "BUSINESS_OWNER", "business-owner", "1"),
                decision(12L, "SECURITY_AUDIT", "security-auditor", "2"),
                decision(13L, "RELEASE_APPROVER", "release-approver", "3"));
        List<ActivationEvidenceRecord> evidence = List.of(
                evidence(21L, 11L, ActivationEvidenceType.ACTIVATION_APPROVAL, APPROVAL),
                evidence(22L, 12L, ActivationEvidenceType.RESOLVER_CONTRACT,
                        RoleDirectoryResolver.CONTRACT_HASH.value()),
                evidence(23L, 12L, ActivationEvidenceType.BINDING, BINDING),
                evidence(24L, 13L, ActivationEvidenceType.CANDIDATE, CANDIDATE),
                evidence(25L, 13L, ActivationEvidenceType.DIRECTORY, DIRECTORY));
        return new ActivationAuditTrail(request, decisions, evidence,
                PersistentActivationCanonical.auditHash(request, decisions, evidence));
    }

    private static PersistentActivationDecision decision(
            Long id, String type, String approver, String hashChar) {
        String sourceHash = hashChar.repeat(64);
        Instant at = NOW.plusSeconds(id);
        String decisionHash = PersistentActivationCanonical.decisionHash(
                "ACT-001", type, approver, "APPROVE", at, sourceHash);
        return new PersistentActivationDecision(id, "ACT-001", type, approver,
                "APPROVE", "approved", sourceHash, decisionHash, ACTIVATION,
                RoleDirectoryResolver.CONTRACT_HASH.value(), BINDING, at, 0);
    }

    private static ActivationEvidenceRecord evidence(
            Long id, Long approvalId, ActivationEvidenceType type, String hash) {
        return new ActivationEvidenceRecord(id, approvalId, "ACT-001", type, hash,
                ACTIVATION, RoleDirectoryResolver.CONTRACT_HASH.value(), BINDING,
                PersistentActivationCanonical.VERSION, NOW.plusSeconds(id));
    }
}
