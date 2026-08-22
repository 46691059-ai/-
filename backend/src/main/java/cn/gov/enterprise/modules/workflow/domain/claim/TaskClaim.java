package cn.gov.enterprise.modules.workflow.domain.claim;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable fact representing the active claim of one Candidate Pool task. */
public record TaskClaim(
        Long id, String claimNo, Long taskId, Long candidatePoolId,
        Long candidateMemberId, Long instanceId, Long nodeExecutionId,
        Long candidateUserId, Long operatorUserId, LocalDateTime claimTime,
        String idempotencyKey, String traceId, TaskClaimStatus status,
        String eligibilitySnapshotHash, String realtimeEligibilityResult,
        String dataScopeResult, String sodResult, String rbacResult,
        String taskStatusBefore, String taskStatusAfter,
        String poolStatusBefore, String poolStatusAfter,
        int taskVersionBefore, int taskVersionAfter, long activeToken, int version,
        Long eligibilityEvidenceId, String eligibilityContractVersion,
        String eligibilityPersistenceHash, String admissionId) {

    public TaskClaim {
        positive(id, "id");
        claimNo = required(claimNo, "claimNo", 100);
        positive(taskId, "taskId"); positive(candidatePoolId, "candidatePoolId");
        positive(candidateMemberId, "candidateMemberId"); positive(instanceId, "instanceId");
        positive(nodeExecutionId, "nodeExecutionId"); positive(candidateUserId, "candidateUserId");
        positive(operatorUserId, "operatorUserId"); Objects.requireNonNull(claimTime, "claimTime");
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
        traceId = required(traceId, "traceId", 64);
        Objects.requireNonNull(status, "status");
        eligibilitySnapshotHash = sha256(eligibilitySnapshotHash, "eligibilitySnapshotHash");
        realtimeEligibilityResult = required(realtimeEligibilityResult, "realtimeEligibilityResult", 65535);
        dataScopeResult = required(dataScopeResult, "dataScopeResult", 1000);
        sodResult = required(sodResult, "sodResult", 1000);
        rbacResult = required(rbacResult, "rbacResult", 1000);
        taskStatusBefore = required(taskStatusBefore, "taskStatusBefore", 30);
        taskStatusAfter = required(taskStatusAfter, "taskStatusAfter", 30);
        poolStatusBefore = required(poolStatusBefore, "poolStatusBefore", 30);
        poolStatusAfter = required(poolStatusAfter, "poolStatusAfter", 30);
        if (taskVersionBefore < 0 || taskVersionAfter != taskVersionBefore + 1) {
            throw new IllegalArgumentException("task claim version transition is invalid");
        }
        if (status == TaskClaimStatus.CLAIMED && activeToken != 0) {
            throw new IllegalArgumentException("active claim token must be zero");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        if (eligibilityEvidenceId == null) {
            if (eligibilityContractVersion != null || eligibilityPersistenceHash != null) {
                throw new IllegalArgumentException("legacy claim cannot carry partial ROLE evidence");
            }
        } else {
            positive(eligibilityEvidenceId, "eligibilityEvidenceId");
            eligibilityContractVersion = required(eligibilityContractVersion,
                    "eligibilityContractVersion", 80);
            if (!"ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1"
                    .equals(eligibilityContractVersion)) {
                throw new IllegalArgumentException("unsupported eligibility contract version");
            }
            if (eligibilityPersistenceHash != null) {
                eligibilityPersistenceHash = sha256(
                        eligibilityPersistenceHash, "eligibilityPersistenceHash");
            }
            admissionId = required(admissionId, "admissionId", 100);
        }
    }

    /** Compatibility constructor for USER/DIRECT and pre-WF5.24 callers. */
    public TaskClaim(Long id, String claimNo, Long taskId, Long candidatePoolId,
            Long candidateMemberId, Long instanceId, Long nodeExecutionId,
            Long candidateUserId, Long operatorUserId, LocalDateTime claimTime,
            String idempotencyKey, String traceId, TaskClaimStatus status,
            String eligibilitySnapshotHash, String realtimeEligibilityResult,
            String dataScopeResult, String sodResult, String rbacResult,
            String taskStatusBefore, String taskStatusAfter,
            String poolStatusBefore, String poolStatusAfter,
            int taskVersionBefore, int taskVersionAfter, long activeToken, int version) {
        this(id, claimNo, taskId, candidatePoolId, candidateMemberId, instanceId,
                nodeExecutionId, candidateUserId, operatorUserId, claimTime,
                idempotencyKey, traceId, status, eligibilitySnapshotHash,
                realtimeEligibilityResult, dataScopeResult, sodResult, rbacResult,
                taskStatusBefore, taskStatusAfter, poolStatusBefore, poolStatusAfter,
                taskVersionBefore, taskVersionAfter, activeToken, version,
                null, null, null, null);
    }

    public static TaskClaim claimed(
            Long id, Long taskId, Long poolId, Long memberId, Long instanceId,
            Long nodeExecutionId, Long claimant, LocalDateTime claimTime,
            String idempotencyKey, String traceId, String eligibilityHash,
            String realtimeResult, String dataScopeResult, String sodResult,
            int taskVersionBefore) {
        return new TaskClaim(id, "WCL-" + id, taskId, poolId, memberId, instanceId,
                nodeExecutionId, claimant, claimant, claimTime, idempotencyKey, traceId,
                TaskClaimStatus.CLAIMED, eligibilityHash, realtimeResult,
                dataScopeResult, sodResult, "ALLOW:workflow:approve",
                "PENDING", "CLAIMED", "AVAILABLE", "CLAIMED",
                taskVersionBefore, taskVersionBefore + 1, 0, 0,
                null, null, null, null);
    }

    public static TaskClaim roleClaimed(
            Long id, Long taskId, Long poolId, Long memberId, Long instanceId,
            Long nodeExecutionId, Long claimant, LocalDateTime claimTime,
            String idempotencyKey, String traceId, String eligibilityHash,
            String realtimeResult, String dataScopeResult, String sodResult,
            int taskVersionBefore, Long evidenceId, String persistenceHash, String admissionId) {
        return new TaskClaim(id, "WCL-" + id, taskId, poolId, memberId, instanceId,
                nodeExecutionId, claimant, claimant, claimTime, idempotencyKey, traceId,
                TaskClaimStatus.CLAIMED, eligibilityHash, realtimeResult,
                dataScopeResult, sodResult, "ALLOW:workflow:approve",
                "PENDING", "CLAIMED", "AVAILABLE", "CLAIMED",
                taskVersionBefore, taskVersionBefore + 1, 0, 0,
                evidenceId, "ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1",
                persistenceHash, admissionId);
    }

    static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static String sha256(String value, String field) {
        String normalized = required(value, field, 64);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        return normalized;
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
