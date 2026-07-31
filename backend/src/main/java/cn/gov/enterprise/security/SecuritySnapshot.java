package cn.gov.enterprise.security;

import java.io.Serializable;
import java.util.Set;

public record SecuritySnapshot(
        Long userId,
        String username,
        Long orgId,
        int tokenVersion,
        Set<String> permissions,
        Set<Long> allowedOrgIds,
        boolean allDataScope,
        boolean selfOnly) implements Serializable {

    public SecurityPrincipal principal() {
        return new SecurityPrincipal(
                userId, username, orgId, allowedOrgIds, allDataScope, selfOnly, tokenVersion);
    }
}
