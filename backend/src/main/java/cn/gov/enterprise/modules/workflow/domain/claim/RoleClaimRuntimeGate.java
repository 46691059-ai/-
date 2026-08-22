package cn.gov.enterprise.modules.workflow.domain.claim;

/** Internal gate only. Approval/eligibility never implies production enablement. */
@FunctionalInterface
public interface RoleClaimRuntimeGate {
    boolean allows(RoleClaimGateContext context);

    record RoleClaimGateContext(long instanceId, long enterpriseId, long definitionId,
            long definitionVersionId, long nodeId, String organizationId,
            String resolverCode, String resolverVersion) { }
}
