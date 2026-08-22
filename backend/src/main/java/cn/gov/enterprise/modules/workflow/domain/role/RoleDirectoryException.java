package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Objects;

/** Domain failure returned by the approval-role directory boundary. */
public final class RoleDirectoryException extends RuntimeException {
    private final RoleDirectoryErrorCode errorCode;

    public RoleDirectoryException(RoleDirectoryErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public RoleDirectoryErrorCode errorCode() {
        return errorCode;
    }
}
