package cn.gov.enterprise.modules.workflow.domain.sandbox;

/** Explicit synthetic fault injection; unavailable outside the Sandbox executor. */
public enum SandboxFault {
    NONE,
    CONTRACT_HASH_DRIFT,
    BINDING_HASH_DRIFT,
    ACTIVATION_NOT_APPROVED
}
