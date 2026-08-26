package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimRuntimeGate;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryRuntimeGate;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Fail-closed production default. Canary integration tests may explicitly opt in. */
@Component
public final class ConfiguredRoleClaimRuntimeGate implements RoleClaimRuntimeGate {
    private final RoleRuntimeGovernanceControlStore controls;
    private final CanaryRuntimeGate canaryGate;

    @Autowired
    public ConfiguredRoleClaimRuntimeGate(RoleRuntimeGovernanceControlStore controls,
            CanaryRuntimeGate canaryGate) {
        this.controls=controls;this.canaryGate=canaryGate;
    }

    @Override public boolean allows(RoleClaimGateContext context) {
        try {
            Instant now=Instant.now();
            String enterprise="ENTERPRISE|"+context.enterpriseId();
            String definition="WORKFLOW_DEFINITION|"+context.enterpriseId()+"|DEF:"+context.definitionId();
            return decision("FEATURE_FLAG","GLOBAL","ON",now)&&decision("FEATURE_FLAG",enterprise,"ON",now)
                    &&decision("FEATURE_FLAG",definition,"ON",now)
                    &&canaryGate.allows(new CanaryScope(context.enterpriseId(),Long.parseLong(context.organizationId()),
                    context.definitionId(),context.definitionVersionId(),context.nodeId(),context.roleCode()),now);
        } catch (RuntimeException ex) { return false; }
    }

    private boolean decision(String type,String scope,String expected,Instant at){
        return controls.latest(type,scope,at).map(c->expected.equals(c.decision())).orElse(false);
    }

}
