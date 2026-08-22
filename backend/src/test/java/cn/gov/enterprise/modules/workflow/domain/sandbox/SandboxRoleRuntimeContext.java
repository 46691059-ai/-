package cn.gov.enterprise.modules.workflow.domain.sandbox;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Frozen, synthetic-only ROLE Sandbox input. */
public record SandboxRoleRuntimeContext(
        String sandboxId,
        String roleCode,
        String organizationId,
        Instant effectiveAt,
        ResolverVersion resolverVersion,
        ResolverContractHash contractHash,
        int candidateLimit) {

    public SandboxRoleRuntimeContext {
        requirePrefix(sandboxId, "SANDBOX-", "sandboxId");
        requirePrefix(roleCode, "SANDBOX_ROLE_", "roleCode");
        requirePrefix(organizationId, "SANDBOX_ORG_", "organizationId");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        if (candidateLimit <= 0) {
            throw new IllegalArgumentException("candidateLimit must be positive");
        }
    }

    static void requirePrefix(String value, String prefix, String field) {
        if (value == null || !value.startsWith(prefix) || value.length() > 100) {
            throw new IllegalArgumentException(field + " must be a synthetic Sandbox identifier");
        }
    }
}
