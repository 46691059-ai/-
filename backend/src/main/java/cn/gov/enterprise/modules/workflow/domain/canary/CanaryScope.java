package cn.gov.enterprise.modules.workflow.domain.canary;

/** Exact, non-wildcard Canary identity. */
public record CanaryScope(long enterpriseId, long organizationId, long definitionId,
        long definitionVersionId, long nodeId, String roleCode) {
    public CanaryScope {
        if (enterpriseId <= 0 || organizationId <= 0 || definitionId <= 0
                || definitionVersionId <= 0 || nodeId <= 0) {
            throw new IllegalArgumentException("Canary scope identifiers must be positive");
        }
        if (roleCode == null || !roleCode.matches("[A-Z0-9_:-]{1,100}")) {
            throw new IllegalArgumentException("roleCode must be an uppercase stable code");
        }
    }

    public static CanaryScope fromRuntime(String enterpriseId,String organizationId,
            String roleCode,String reference) {
        if (reference == null || !reference.matches("DEF:[1-9][0-9]*\\|VER:[1-9][0-9]*\\|NODE:[1-9][0-9]*")) {
            throw new IllegalArgumentException("invalid exact Canary business scope reference");
        }
        String[] parts=reference.split("\\|");
        return new CanaryScope(Long.parseLong(enterpriseId),Long.parseLong(organizationId),
                Long.parseLong(parts[0].substring(4)),Long.parseLong(parts[1].substring(4)),
                Long.parseLong(parts[2].substring(5)),roleCode);
    }
}
