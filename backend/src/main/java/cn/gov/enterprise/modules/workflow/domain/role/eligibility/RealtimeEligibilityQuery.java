package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.Objects;

/** Query is reconstructed from trusted Workflow facts; it is not a controller request DTO. */
public record RealtimeEligibilityQuery(
        String enterpriseId,
        long workflowInstanceId,
        long taskId,
        long nodeExecutionId,
        long candidatePoolId,
        long candidateUserId,
        String roleCode,
        String organizationId,
        Instant claimAt,
        String candidateDirectoryRevision,
        String candidateDirectoryResultHash,
        String candidatePoolHash,
        String runtimeBindingHash,
        String businessScopeReference,
        String correlationId) {

    public RealtimeEligibilityQuery {
        enterpriseId = text(enterpriseId, "enterpriseId", 100);
        positive(workflowInstanceId, "workflowInstanceId");
        positive(taskId, "taskId");
        positive(nodeExecutionId, "nodeExecutionId");
        positive(candidatePoolId, "candidatePoolId");
        positive(candidateUserId, "candidateUserId");
        roleCode = upperCode(roleCode, "roleCode");
        organizationId = text(organizationId, "organizationId", 100);
        Objects.requireNonNull(claimAt, "claimAt");
        candidateDirectoryRevision = text(candidateDirectoryRevision, "candidateDirectoryRevision", 100);
        candidateDirectoryResultHash = hash(candidateDirectoryResultHash, "candidateDirectoryResultHash");
        candidatePoolHash = hash(candidatePoolHash, "candidatePoolHash");
        runtimeBindingHash = hash(runtimeBindingHash, "runtimeBindingHash");
        businessScopeReference = text(businessScopeReference, "businessScopeReference", 200);
        correlationId = text(correlationId, "correlationId", 100);
    }

    static String text(String value, String name, int max) {
        return RealtimeEligibilityFailure.requireText(value, name, max);
    }

    static String upperCode(String value, String name) {
        String normalized = text(value, name, 64);
        if (!normalized.matches("[A-Z0-9_:-]+")) {
            throw new IllegalArgumentException(name + " must be an uppercase stable code");
        }
        return normalized;
    }

    static String hash(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
