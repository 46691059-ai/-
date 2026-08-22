package cn.gov.enterprise.modules.workflow.domain.role;

/** Explicit validation outcome used before proposal-set freezing. */
public record RoleBindingValidationResult(Status status, String message) {
    public enum Status {
        VALID,
        INVALID_ROLE,
        INVALID_CONTRACT,
        INVALID_HASH,
        INVALID_ORGANIZATION
    }

    public RoleBindingValidationResult {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        message = message == null ? "" : message.trim();
    }

    public boolean valid() { return status == Status.VALID; }
    public static RoleBindingValidationResult validResult() {
        return new RoleBindingValidationResult(Status.VALID, "valid");
    }
    public static RoleBindingValidationResult invalid(Status status, String message) {
        if (status == Status.VALID) throw new IllegalArgumentException("invalid result cannot be VALID");
        return new RoleBindingValidationResult(status, message);
    }
}
