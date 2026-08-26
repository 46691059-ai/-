package cn.gov.enterprise.modules.workflow.domain.claim;

import cn.gov.enterprise.security.SecurityPrincipal;
import java.time.Instant;

/** Short-transaction recheck. Prepare evidence never substitutes this current-policy fence. */
@FunctionalInterface
public interface RoleClaimCommitCapabilityGate {
    Decision verify(Facts facts);
    record Facts(long instanceId,long enterpriseId,long definitionId,long definitionVersionId,long nodeId,
            String businessType,String businessKey,String organizationId,String roleCode,long initiatorUserId,long initiatorOrgId,
            SecurityPrincipal principal,Instant checkedAt) { }
    record Decision(boolean allowed,String reasonCode,String evidenceHash) { }
}
