package cn.gov.enterprise.modules.workflow.domain.sandbox;

/** Synthetic claim choice only; never a workflow_task_claim persistence object. */
public record SandboxClaimSimulation(
        String sandboxId, String syntheticUserId, String candidateHash, String simulationHash) {
    public SandboxClaimSimulation {
        SandboxRoleRuntimeContext.requirePrefix(sandboxId, "SANDBOX-", "sandboxId");
        SandboxRoleRuntimeContext.requirePrefix(
                syntheticUserId, "SANDBOX_USER_", "syntheticUserId");
        validateHash(candidateHash, "candidateHash");
        validateHash(simulationHash, "simulationHash");
    }

    private static void validateHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
