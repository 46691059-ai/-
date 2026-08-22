package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivation;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationApprovedRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.ApproverRole;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationDecision.Decision;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationGate;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationRequest;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RuntimeEligibilityRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeActivationGateTest {
    private static final Instant NOW = Instant.parse("2026-08-13T08:00:00Z");
    private static final String BINDING = "b".repeat(64);
    private static final String CANDIDATE = "c".repeat(64);
    private static final String DIRECTORY = "d".repeat(64);

    @Test
    void eligibleRequestRequiresThreeRaciApprovalsAndProducesImmutableSnapshot() {
        RoleRuntimeActivation activation = eligibleActivation();
        activation = activation.decide(approve(ApproverRole.BUSINESS_OWNER, "1"));
        activation = activation.decide(approve(ApproverRole.SECURITY_AUDIT, "2"));
        assertThat(activation.status()).isEqualTo(RoleRuntimeActivationStatus.ELIGIBLE);
        activation = activation.decide(approve(ApproverRole.RELEASE_APPROVER, "3"));

        assertThat(activation.status()).isEqualTo(RoleRuntimeActivationStatus.APPROVED);
        assertThat(activation.status()).isNotEqualTo(RoleRuntimeActivationStatus.ENABLED);
        RoleRuntimeActivationSnapshot snapshot = activation.snapshot();
        assertThat(snapshot.decisions()).hasSize(3);
        assertThat(snapshot.evidenceHash()).matches("[0-9a-f]{64}");

        RoleRuntimeActivationApprovedRegistry registry = new RoleRuntimeActivationApprovedRegistry();
        registry.register(snapshot);
        assertThat(registry.approved(snapshot.activationHash())).isTrue();
        assertThatThrownBy(() -> registry.register(snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate Activation approval");
    }

    @Test
    void missingApprovalMustNotProduceSnapshot() {
        RoleRuntimeActivation activation = eligibleActivation()
                .decide(approve(ApproverRole.BUSINESS_OWNER, "1"))
                .decide(approve(ApproverRole.SECURITY_AUDIT, "2"));

        assertThatThrownBy(activation::snapshot)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void resolverContractBindingAndCandidateDriftMustFailClosed() {
        RoleRuntimeActivationRequest request = request();
        assertBlocked(request, evidence("0".repeat(64), BINDING, CANDIDATE, DIRECTORY,
                true, true, true), "RESOLVER_CONTRACT_HASH_MISMATCH");
        assertBlocked(request, evidence(request.contractHash().value(), "0".repeat(64),
                CANDIDATE, DIRECTORY, true, true, true), "BINDING_HASH_MISMATCH");
        assertBlocked(request, evidence(request.contractHash().value(), BINDING,
                "0".repeat(64), DIRECTORY, true, true, true), "CANDIDATE_RULE_MISMATCH");
        assertBlocked(request, evidence(request.contractHash().value(), BINDING,
                CANDIDATE, DIRECTORY, false, true, true), "CANDIDATE_RULE_MISMATCH");
    }

    @Test
    void directorySodAuditAndScopeMustFailClosedWithoutFallback() {
        RoleRuntimeActivationRequest request = request();
        assertBlocked(request, evidence(request.contractHash().value(), BINDING,
                CANDIDATE, "0".repeat(64), true, true, true),
                "DIRECTORY_CONTRACT_MISMATCH");
        assertBlocked(request, evidence(request.contractHash().value(), BINDING,
                CANDIDATE, DIRECTORY, true, false, true),
                "SEGREGATION_OF_DUTIES_BLOCKED");
        assertBlocked(request, evidence(request.contractHash().value(), BINDING,
                CANDIDATE, DIRECTORY, true, true, false),
                "AUDIT_EVIDENCE_INCOMPLETE");
        RoleRuntimeActivationEvidence scopeDrift = new RoleRuntimeActivationEvidence(
                request.contractHash().value(), BINDING, CANDIDATE, DIRECTORY,
                "OTHER_SCOPE", NOW, true, true, true);
        assertBlocked(request, scopeDrift, "ACTIVATION_SCOPE_MISMATCH");
    }

    @Test
    void duplicateApprovalAndWorkflowAdministratorApprovalMustBeRejected() {
        RoleRuntimeActivation activation = eligibleActivation()
                .decide(approve(ApproverRole.BUSINESS_OWNER, "1"));
        assertThatThrownBy(() -> activation.decide(
                approve(ApproverRole.BUSINESS_OWNER, "2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
        RoleRuntimeActivation eligible = eligibleActivation();
        assertThatThrownBy(() -> eligible.decide(
                approve(ApproverRole.WORKFLOW_ADMIN, "3")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no Activation approval right");
    }

    @Test
    void rejectionAndRevocationMustBlockSnapshotAndNeverEnableRuntime() {
        RoleRuntimeActivation rejected = eligibleActivation().decide(
                decision(Decision.REJECT, ApproverRole.SECURITY_AUDIT, "risk rejected", "4"));
        assertThat(rejected.status()).isEqualTo(RoleRuntimeActivationStatus.REJECTED);
        assertThatThrownBy(rejected::snapshot).isInstanceOf(IllegalStateException.class);

        RoleRuntimeActivation approved = approvedActivation();
        RoleRuntimeActivation revoked = approved.decide(
                decision(Decision.REVOKE, ApproverRole.RELEASE_APPROVER,
                        "release approval revoked", "5"));
        assertThat(revoked.status()).isEqualTo(RoleRuntimeActivationStatus.REVOKED);
        assertThat(revoked.status()).isNotEqualTo(RoleRuntimeActivationStatus.ENABLED);
        assertThatThrownBy(revoked::snapshot).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.decide(
                decision(Decision.REVOKE, ApproverRole.BUSINESS_OWNER,
                        "unauthorized revoke", "6")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Release Approver");
    }

    @Test
    void canonicalHashMustChangeForEveryActivationCriticalFactor() {
        RoleRuntimeActivationRequest base = request();
        List<RoleRuntimeActivationRequest> changed = List.of(
                new RoleRuntimeActivationRequest(base.resolverCode(), base.resolverVersion(),
                        ResolverContractHash.of("0".repeat(64)), BINDING, CANDIDATE,
                        DIRECTORY, NOW, "INVESTMENT_DECISION", "requester"),
                requestWith(BINDING.replace('b', '1'), CANDIDATE, DIRECTORY,
                        NOW, "INVESTMENT_DECISION"),
                requestWith(BINDING, CANDIDATE.replace('c', '2'), DIRECTORY,
                        NOW, "INVESTMENT_DECISION"),
                requestWith(BINDING, CANDIDATE, DIRECTORY.replace('d', '3'),
                        NOW, "INVESTMENT_DECISION"),
                requestWith(BINDING, CANDIDATE, DIRECTORY,
                        NOW.plusSeconds(1), "INVESTMENT_DECISION"),
                requestWith(BINDING, CANDIDATE, DIRECTORY,
                        NOW, "CONTRACT_APPROVAL"));
        assertThat(changed).extracting(RoleRuntimeActivationRequest::canonicalHash)
                .doesNotContain(base.canonicalHash());
    }

    @Test
    void explicitUserAndLegacyBehaviorRemainActiveWhileRoleResolverCannotExecute() {
        ResolverRegistry registry = registry();
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor().status())
                .isEqualTo(ResolverStatus.ACTIVE);
        assertThat(registry.requireDescriptor(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION).status()).isEqualTo(ResolverStatus.PREPARED);
        assertThatThrownBy(() -> registry.require(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH))
                .isInstanceOf(AssignmentResolverRegistryException.class);
    }

    @Test
    void activationDomainMustRemainFrameworkAndPersistenceFree() {
        List<Class<?>> types = List.of(
                RoleRuntimeActivation.class,
                RoleRuntimeActivationRequest.class,
                RoleRuntimeActivationDecision.class,
                RoleRuntimeActivationSnapshot.class,
                RoleRuntimeActivationGate.class,
                RoleRuntimeActivationApprovedRegistry.class);
        assertThat(types.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType).map(Class::getName))
                .noneMatch(name -> name.startsWith("org.springframework")
                        || name.startsWith("com.baomidou")
                        || name.contains(".infrastructure.persistence.entity"));
    }

    private static RoleRuntimeActivation eligibleActivation() {
        RoleRuntimeActivationRequest request = request();
        return RoleRuntimeActivation.draft(request).evaluate(gate(), evidence(
                request.contractHash().value(), BINDING, CANDIDATE, DIRECTORY,
                true, true, true));
    }

    private static RoleRuntimeActivation approvedActivation() {
        return eligibleActivation()
                .decide(approve(ApproverRole.BUSINESS_OWNER, "1"))
                .decide(approve(ApproverRole.SECURITY_AUDIT, "2"))
                .decide(approve(ApproverRole.RELEASE_APPROVER, "3"));
    }

    private static RoleRuntimeActivationRequest request() {
        return requestWith(BINDING, CANDIDATE, DIRECTORY, NOW, "INVESTMENT_DECISION");
    }

    private static RoleRuntimeActivationRequest requestWith(
            String binding, String candidate, String directory,
            Instant effectiveAt, String scope) {
        return new RoleRuntimeActivationRequest(RoleDirectoryResolver.CODE,
                RoleDirectoryResolver.VERSION, RoleDirectoryResolver.CONTRACT_HASH,
                binding, candidate, directory, effectiveAt, scope, "requester");
    }

    private static RoleRuntimeActivationEvidence evidence(
            String resolverContract, String binding, String candidate, String directory,
            boolean candidateValid, boolean sodAllowed, boolean auditComplete) {
        return new RoleRuntimeActivationEvidence(resolverContract, binding, candidate,
                directory, "INVESTMENT_DECISION", NOW,
                candidateValid, sodAllowed, auditComplete);
    }

    private static RoleRuntimeActivationGate gate() {
        return new RoleRuntimeActivationGate(new RuntimeEligibilityRegistry(registry(), Set.of()));
    }

    private static ResolverRegistry registry() {
        ExplicitUserResolver explicit = new ExplicitUserResolver();
        return new ResolverRegistry(List.of(explicit.descriptor(),
                RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of(explicit));
    }

    private static RoleRuntimeActivationDecision approve(ApproverRole role, String evidence) {
        return decision(Decision.APPROVE, role, "approved governance evidence", evidence);
    }

    private static RoleRuntimeActivationDecision decision(
            Decision decision, ApproverRole role, String reason, String evidence) {
        return new RoleRuntimeActivationDecision(decision, role, role.name(), reason,
                NOW.plusSeconds(role.ordinal()), evidence.repeat(64));
    }

    private static void assertBlocked(
            RoleRuntimeActivationRequest request,
            RoleRuntimeActivationEvidence evidence,
            String reason) {
        RoleRuntimeActivation result = RoleRuntimeActivation.draft(request)
                .evaluate(gate(), evidence);
        assertThat(result.status()).isEqualTo(RoleRuntimeActivationStatus.BLOCKED);
        assertThat(result.reason()).isEqualTo(reason);
    }
}
