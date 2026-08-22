package cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence;

import java.time.Instant;
import java.util.List;

/** Immutable persistence aggregate. It is evidence only and cannot enable ROLE runtime. */
public record RoleRealtimeEligibilityPersistenceBundle(
        Header header,
        List<ValidatorEvidence> validators,
        List<CapabilityEvidence> capabilities,
        LifecycleEvent initialEvent) {

    public RoleRealtimeEligibilityPersistenceBundle {
        validators = List.copyOf(validators);
        capabilities = List.copyOf(capabilities);
    }

    public record Header(
            long id, String evidenceId, String eligibilityRequestId, String claimRequestId,
            String claimIdempotencyKey, int attemptNo, String correlationId,
            long instanceId, long definitionVersionId, long nodeId, long nodeExecutionId,
            long taskId, long candidatePoolId, long candidateMemberId, long candidateUserId,
            long bindingSetId, long resolverBindingId, long nodeResolverBindingId,
            String roleCode, String organizationId, String candidatePoolHash,
            String runtimeBindingHash, String eligibilityHash, String candidateDirectoryRevision,
            String claimDirectoryRevision, String directoryResultHash, String directoryContractHash,
            boolean directoryComplete, Instant directoryEffectiveAt, Instant directoryCheckedAt,
            String decision, int terminalValidatorOrder, int validatorCount, int capabilityCount,
            String validatorRootHash, String capabilityRootHash, String persistenceHash,
            String policyVersion, String evidenceSource, Instant claimAt, Instant verifiedAt,
            Instant expiresAt) { }

    public record ValidatorEvidence(
            long id, long evidenceRowId, String code, int order, String status,
            String reasonCode, String evidenceHash, Instant checkedAt) { }

    public record CapabilityEvidence(
            long id, long evidenceRowId, String capabilityCode, String validatorCode,
            String status, String decision, String providerVersion, String policyVersion,
            String evidenceHash, Instant checkedAt, Instant validUntil) { }

    public record LifecycleEvent(
            long id, long evidenceRowId, long sequenceNo, String eventType, Long claimId,
            String reasonCode, String previousEventHash, String eventHash, Instant occurredAt,
            String operatorId, String idempotencyKey) { }
}
