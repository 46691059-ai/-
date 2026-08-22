package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.EnumSet;

/** Frozen WF5.1 retry semantics: at most initial attempt plus one retry. */
public final class RoleDirectoryRetryPolicy {
    public static final int MAX_ATTEMPTS = 2;
    private static final EnumSet<RoleDirectoryErrorCode> RETRYABLE = EnumSet.of(
            RoleDirectoryErrorCode.DIRECTORY_TIMEOUT,
            RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE);

    public boolean mayRetry(RoleDirectoryErrorCode code, int attemptsMade) {
        return RETRYABLE.contains(code) && attemptsMade < MAX_ATTEMPTS;
    }
}
