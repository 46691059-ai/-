package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationPersistencePolicy;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivation;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationGate;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimePersistentActivationTest {
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final String BINDING = "b".repeat(64);
    private static final String CANDIDATE = "c".repeat(64);
    private static final String DIRECTORY = "d".repeat(64);

    @Test
    void approvedEvidenceMustFreezeAsCompleteAppendOnlyAuditTrail() {
        AtomicLong ids = new AtomicLong(100);
        var trail = new ActivationPersistencePolicy().freezeApproved(
                "ACT-001", approved(), 17, ids::incrementAndGet);
        assertThat(trail.request().status()).isEqualTo(PersistentActivationStatus.PERSISTED);
        assertThat(trail.decisions()).hasSize(3);
        assertThat(trail.evidence()).hasSize(5);
        assertThat(trail.auditHash()).matches("[0-9a-f]{64}");
        assertThat(trail.request().status()).isNotEqualTo(PersistentActivationStatus.DRAFT);
    }

    @Test
    void missingApprovalAndHashDriftMustFailClosed() {
        RoleRuntimeActivation partial = eligible().decide(approve(
                RoleRuntimeActivationDecision.ApproverRole.BUSINESS_OWNER, "1"));
        assertThatThrownBy(partial::snapshot).isInstanceOf(IllegalStateException.class);
        RoleRuntimeActivationSnapshot snapshot = approved();
        assertThatThrownBy(() -> new PersistentActivationRequest(1L, "ACT", "ROLE_DIRECTORY",
                "ROLE_DIRECTORY_V1", snapshot.request().contractHash().value(), BINDING,
                CANDIDATE, DIRECTORY, 1, "A".repeat(64), snapshot.evidenceHash(),
                "SCOPE", NOW, "owner", PersistentActivationStatus.PERSISTED, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("activationHash");
    }

    @Test
    void stateMachineMustNeverTreatPersistedAsEnabled() {
        PersistentActivationRequest draft = request(PersistentActivationStatus.DRAFT);
        PersistentActivationRequest persisted = draft.transitionTo(PersistentActivationStatus.SUBMITTED)
                .transitionTo(PersistentActivationStatus.APPROVAL_PENDING)
                .transitionTo(PersistentActivationStatus.APPROVED)
                .transitionTo(PersistentActivationStatus.PERSISTED);
        assertThat(persisted.status()).isEqualTo(PersistentActivationStatus.PERSISTED);
        assertThat(Arrays.stream(PersistentActivationStatus.values()).map(Enum::name))
                .doesNotContain("ENABLED");
        assertThatThrownBy(() -> persisted.transitionTo(PersistentActivationStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canonicalHashMustBeStableAndSensitiveToDirectoryRevision() {
        var left = new ActivationPersistencePolicy().freezeApproved("ACT-001", approved(), 17,
                new AtomicLong(1)::incrementAndGet);
        var right = new ActivationPersistencePolicy().freezeApproved("ACT-001", approved(), 17,
                new AtomicLong(50)::incrementAndGet);
        var changed = new ActivationPersistencePolicy().freezeApproved("ACT-001", approved(), 18,
                new AtomicLong(80)::incrementAndGet);
        assertThat(left.auditHash()).isEqualTo(right.auditHash());
        assertThat(changed.auditHash()).isNotEqualTo(left.auditHash());
    }

    @Test
    void persistenceDomainMustRemainFrameworkAndEntityFree() {
        List<Class<?>> types = List.of(PersistentActivationRequest.class,
                cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision.class,
                cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord.class,
                cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail.class,
                ActivationPersistencePolicy.class);
        assertThat(types.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.startsWith("org.springframework")
                        || name.startsWith("com.baomidou") || name.contains(".entity"));
    }

    private static PersistentActivationRequest request(PersistentActivationStatus status) {
        return new PersistentActivationRequest(1L, "ACT-STATE", "ROLE_DIRECTORY",
                "ROLE_DIRECTORY_V1", "a".repeat(64), BINDING, CANDIDATE, DIRECTORY,
                1, "e".repeat(64), "f".repeat(64), "SCOPE", NOW, "owner", status, 0);
    }

    private static RoleRuntimeActivationSnapshot approved() {
        return eligible().decide(approve(RoleRuntimeActivationDecision.ApproverRole.BUSINESS_OWNER, "1"))
                .decide(approve(RoleRuntimeActivationDecision.ApproverRole.SECURITY_AUDIT, "2"))
                .decide(approve(RoleRuntimeActivationDecision.ApproverRole.RELEASE_APPROVER, "3"))
                .snapshot();
    }

    private static RoleRuntimeActivation eligible() {
        RoleRuntimeActivationRequest request = new RoleRuntimeActivationRequest(
                RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                RoleDirectoryResolver.CONTRACT_HASH, BINDING, CANDIDATE, DIRECTORY,
                NOW, "INVESTMENT_DECISION", "requester");
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        ResolverRegistry registry = new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
        RoleRuntimeActivationGate gate = new RoleRuntimeActivationGate(
                new RuntimeEligibilityRegistry(registry, Set.of()));
        return RoleRuntimeActivation.draft(request).evaluate(gate,
                new RoleRuntimeActivationEvidence(request.contractHash().value(), BINDING,
                        CANDIDATE, DIRECTORY, "INVESTMENT_DECISION", NOW, true, true, true));
    }

    private static RoleRuntimeActivationDecision approve(
            RoleRuntimeActivationDecision.ApproverRole role, String hashChar) {
        return new RoleRuntimeActivationDecision(RoleRuntimeActivationDecision.Decision.APPROVE,
                role, role.name(), "approved", NOW.plusSeconds(role.ordinal()), hashChar.repeat(64));
    }
}
