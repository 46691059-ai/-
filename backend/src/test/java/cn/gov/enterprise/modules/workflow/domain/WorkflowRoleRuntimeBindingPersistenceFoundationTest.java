package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceType;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleStatus;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPersistenceCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPersistencePolicy;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionRequest;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeBindingPersistenceFoundationTest {
    private static final Instant EFFECTIVE = Instant.parse("2026-08-13T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final String ACTIVATION = "a".repeat(64);
    private static final String BINDING = "b".repeat(64);
    private static final String CANDIDATE = "c".repeat(64);
    private static final String DIRECTORY = "d".repeat(64);
    private static final String PERMISSION = "e".repeat(64);
    private static final String APPROVAL = "f".repeat(64);

    @Test
    void promotedCandidateMustFreezeAsImmutablePreRuntimeSnapshotAndHashChain() {
        ActivationAuditTrail trail = trail();
        RuntimeBindingPromotionDecision decision = promotionPolicy().promote(
                promotionRequest(), RuntimeBindingPromotionEvidence.from(trail), NOW);
        var bundle = new RoleRuntimeBindingPersistencePolicy().freeze("SNAPSHOT-001", trail,
                decision, "persistence-operator", NOW, new AtomicLong(100)::incrementAndGet);

        assertThat(bundle.snapshot().snapshotHash()).matches("[0-9a-f]{64}");
        assertThat(bundle.snapshot().canonicalVersion())
                .isEqualTo(RoleRuntimeBindingPersistenceCanonical.VERSION);
        assertThat(bundle.lifecycleEvents()).extracting(event -> event.toStatus())
                .containsExactly(RoleRuntimeBindingLifecycleStatus.CREATED,
                        RoleRuntimeBindingLifecycleStatus.VALIDATED);
        assertThat(bundle.lifecycleEvents()).allSatisfy(event -> {
            assertThat(event.snapshotRowId()).isEqualTo(bundle.snapshot().id());
            assertThat(event.promotionId()).isEqualTo(bundle.promotion().promotionId());
            assertThat(event.activationId()).isEqualTo(bundle.promotion().activationId());
        });
    }

    @Test
    void evidenceDriftAndIllegalLifecycleTransitionMustFailClosed() {
        ActivationAuditTrail trail = trail();
        RuntimeBindingPromotionDecision decision = promotionPolicy().promote(
                promotionRequest(), RuntimeBindingPromotionEvidence.from(trail), NOW);
        var bundle = new RoleRuntimeBindingPersistencePolicy().freeze("SNAPSHOT-001", trail,
                decision, "operator", NOW, new AtomicLong(200)::incrementAndGet);
        assertThatThrownBy(() -> new RoleRuntimeBindingPersistencePolicy().nextEvent(999L,
                bundle.lifecycleEvents().get(1), RoleRuntimeBindingLifecycleStatus.CREATED,
                "ILLEGAL", bundle.snapshot().snapshotHash(), NOW, "operator"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RoleRuntimeBindingPersistencePolicy().freeze(
                "SNAPSHOT-002", trail(), decision, "operator", EFFECTIVE.minusSeconds(1),
                new AtomicLong(300)::incrementAndGet))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("effective scope");
    }

    @Test
    void persistenceModelsMustBeFrameworkFreeAndRoleRuntimeRemainDisabled() {
        List<Class<?>> types = List.of(
                cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPromotion.class,
                cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingCandidateSnapshot.class,
                cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleEvent.class,
                RoleRuntimeBindingPersistencePolicy.class);
        assertThat(types.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.startsWith("org.springframework")
                        || name.startsWith("com.baomidou") || name.contains(".entity"));
        assertThat(registry().requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        assertThat(registry().require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
    }

    private static RuntimeBindingPromotionPolicy promotionPolicy() {
        return new RuntimeBindingPromotionPolicy(new RuntimeEligibilityRegistry(registry(), Set.of()));
    }

    private static ResolverRegistry registry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RuntimeBindingPromotionRequest promotionRequest() {
        return new RuntimeBindingPromotionRequest("PROMOTION-001", "ACT-001", ACTIVATION,
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                RoleDirectoryResolver.CONTRACT_HASH, BINDING, CANDIDATE, 17,
                "INVESTMENT_DECISION", EFFECTIVE, NOW.plusSeconds(3600),
                "release-approver", true, PERMISSION);
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
        return new PersistentActivationDecision(id, "ACT-001", type, approver,
                "APPROVE", "approved", sourceHash,
                PersistentActivationCanonical.decisionHash(
                        "ACT-001", type, approver, "APPROVE", at, sourceHash),
                ACTIVATION, RoleDirectoryResolver.CONTRACT_HASH.value(), BINDING, at, 0);
    }

    private static ActivationEvidenceRecord evidence(
            Long id, Long approvalId, ActivationEvidenceType type, String hash) {
        return new ActivationEvidenceRecord(id, approvalId, "ACT-001", type, hash,
                ACTIVATION, RoleDirectoryResolver.CONTRACT_HASH.value(), BINDING,
                PersistentActivationCanonical.VERSION, NOW.plusSeconds(id));
    }
}
