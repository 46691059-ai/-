package cn.gov.enterprise.modules.workflow.domain.sandbox;

/** Complete Sandbox result. IDs are hashes only; no runtime entity is exposed. */
public record SandboxExecutionResult(
        String bindingHash,
        String candidateHash,
        String activationHash,
        SandboxExecutionStatus executionStatus,
        SandboxAuditEvidence auditEvidence,
        SandboxClaimSimulation claimSimulation,
        String failureReason) {

    public SandboxExecutionResult {
        if (executionStatus == null) throw new IllegalArgumentException("executionStatus is required");
        if (executionStatus == SandboxExecutionStatus.COMPLETED) {
            validateHash(bindingHash, "bindingHash");
            validateHash(candidateHash, "candidateHash");
            validateHash(activationHash, "activationHash");
            if (auditEvidence == null || claimSimulation == null) {
                throw new IllegalArgumentException("completed Sandbox requires evidence and claim simulation");
            }
            failureReason = "";
        } else if (executionStatus == SandboxExecutionStatus.FAILED) {
            if (failureReason == null || failureReason.isBlank()) {
                throw new IllegalArgumentException("failed Sandbox requires a reason");
            }
        }
    }

    private static void validateHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
