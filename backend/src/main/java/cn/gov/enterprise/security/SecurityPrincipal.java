package cn.gov.enterprise.security;

import java.io.Serializable;
import java.util.Set;

public record SecurityPrincipal(
        Long userId,
        String username,
        Long orgId,
        Set<Long> allowedOrgIds,
        boolean allDataScope,
        boolean selfOnly,
        int tokenVersion) implements Serializable {
}
