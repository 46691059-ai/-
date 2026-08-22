package cn.gov.enterprise.modules.workflow.domain.role;

/** Explicit ROLE_RUNTIME_CANONICAL_V1 serializer. */
final class RoleRuntimeCanonical {
    private RoleRuntimeCanonical() { }

    static String hash(RoleRuntimeEligibility eligibility,
            String candidateHash, int candidateCount, int candidateLimit) {
        String canonical = "{\"bindingHash\":\"" + eligibility.bindingHash()
                + "\",\"candidateCount\":" + candidateCount
                + ",\"candidateHash\":\"" + candidateHash
                + "\",\"candidateLimit\":" + candidateLimit
                + ",\"organizationId\":\"" + escape(eligibility.organizationId())
                + "\",\"resolverCode\":\"" + eligibility.resolverCode().value()
                + "\",\"resolverContractHash\":\"" + eligibility.contractHash().value()
                + "\",\"resolverVersion\":\"" + eligibility.resolverVersion().value()
                + "\",\"roleCode\":\"" + escape(eligibility.roleCode())
                + "\",\"schema\":\"ROLE_RUNTIME_CANONICAL_V1\"}";
        return RoleDirectoryCanonical.sha256(canonical);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
