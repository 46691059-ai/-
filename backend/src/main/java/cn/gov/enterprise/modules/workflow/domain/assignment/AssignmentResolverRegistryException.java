package cn.gov.enterprise.modules.workflow.domain.assignment;

/** Stable failure classification for resolver discovery. */
public final class AssignmentResolverRegistryException extends RuntimeException {
    public enum Reason {
        NOT_FOUND, DISABLED, VERSION_MISMATCH, CONTRACT_HASH_MISMATCH,
        STATUS_NOT_ACTIVE, INVALID_REGISTRATION
    }

    private final Reason reason;

    public AssignmentResolverRegistryException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
