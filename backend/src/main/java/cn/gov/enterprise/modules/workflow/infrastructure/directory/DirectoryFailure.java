package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.util.Objects;

/** Stable infrastructure failure classification; no fallback semantics are implied. */
public final class DirectoryFailure extends RuntimeException {
    public enum Code {
        NETWORK_TIMEOUT(true), TEMPORARY_UNAVAILABLE(true), TRANSIENT_DEPENDENCY_FAILURE(true),
        ROLE_NOT_FOUND(false), ROLE_DISABLED(false), ORG_INACTIVE(false),
        ROLE_ORG_MISMATCH(false), PARTIAL_RESULT(false), SOURCE_CONFLICT(false),
        CONTRACT_MISMATCH(false), REVISION_MISMATCH(false), HASH_MISMATCH(false),
        CANDIDATE_LIMIT_EXCEEDED(false), INVALID_CONFIGURATION(false), CIRCUIT_OPEN(false);

        private final boolean retryable;
        Code(boolean retryable) { this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }

    private final Code code;

    public DirectoryFailure(Code code, String message) {
        this(code, message, null);
    }

    public DirectoryFailure(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() { return code; }
}
