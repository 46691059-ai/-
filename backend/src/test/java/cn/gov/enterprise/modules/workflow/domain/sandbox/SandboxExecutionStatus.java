package cn.gov.enterprise.modules.workflow.domain.sandbox;

public enum SandboxExecutionStatus {
    CREATED,
    DIRECTORY_RESOLVED,
    BINDING_GENERATED,
    ELIGIBILITY_PASSED,
    ACTIVATION_APPROVED,
    CANDIDATE_GENERATED,
    COMPLETED,
    FAILED
}
