package cn.gov.enterprise.modules.workflow.domain.claim;

import java.time.Instant;

/** Read-only bridge from the persisted Execution Admission to ROLE Claim. */
@FunctionalInterface
public interface RoleClaimAdmissionGate {
    Decision verify(Facts facts);

    record Facts(long instanceId,long definitionVersionId,long nodeId,String resolverCode,
            String resolverVersion,String resolverContractHash,String runtimeBindingHash,
            String directoryRevision,long enterpriseId,Instant now) { }
    record Decision(boolean allowed,String reasonCode,String admissionId,String admissionHash) {
        public static Decision allow(String id,String hash){return new Decision(true,"ALLOW",id,hash);}
        public static Decision deny(String reason){return new Decision(false,reason,null,null);}
    }
}
