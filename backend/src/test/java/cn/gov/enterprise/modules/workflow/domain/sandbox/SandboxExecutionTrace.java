package cn.gov.enterprise.modules.workflow.domain.sandbox;

/** Strict linear Sandbox state machine; FAILED is terminal from every non-terminal state. */
final class SandboxExecutionTrace {
    private SandboxExecutionStatus status = SandboxExecutionStatus.CREATED;

    void advance(SandboxExecutionStatus next) {
        if (next == SandboxExecutionStatus.FAILED) {
            if (status == SandboxExecutionStatus.COMPLETED) {
                throw new IllegalStateException("completed Sandbox cannot fail");
            }
            status = next;
            return;
        }
        SandboxExecutionStatus expected = switch (status) {
            case CREATED -> SandboxExecutionStatus.DIRECTORY_RESOLVED;
            case DIRECTORY_RESOLVED -> SandboxExecutionStatus.BINDING_GENERATED;
            case BINDING_GENERATED -> SandboxExecutionStatus.ELIGIBILITY_PASSED;
            case ELIGIBILITY_PASSED -> SandboxExecutionStatus.ACTIVATION_APPROVED;
            case ACTIVATION_APPROVED -> SandboxExecutionStatus.CANDIDATE_GENERATED;
            case CANDIDATE_GENERATED -> SandboxExecutionStatus.COMPLETED;
            default -> null;
        };
        if (next != expected) {
            throw new IllegalStateException("illegal Sandbox transition: " + status + " -> " + next);
        }
        status = next;
    }

    SandboxExecutionStatus status() { return status; }
}
