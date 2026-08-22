package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Objects;

/** Candidate draft adaptation failure. */
public final class RoleCandidateException extends RuntimeException {
    private final RoleCandidateErrorCode errorCode;

    public RoleCandidateException(RoleCandidateErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public RoleCandidateErrorCode errorCode() { return errorCode; }
}
