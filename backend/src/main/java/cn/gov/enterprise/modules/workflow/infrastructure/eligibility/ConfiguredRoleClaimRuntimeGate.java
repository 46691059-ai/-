package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimRuntimeGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Fail-closed production default. Canary integration tests may explicitly opt in. */
@Component
public final class ConfiguredRoleClaimRuntimeGate implements RoleClaimRuntimeGate {
    private final boolean enabled;
    private final String killSwitch;
    private final Long enterpriseId;
    private final Long definitionId;
    private final Long definitionVersionId;
    private final Long nodeId;
    private final RoleRuntimeGovernanceControlStore controls;

    @Autowired
    public ConfiguredRoleClaimRuntimeGate(RoleRuntimeGovernanceControlStore controls,
            @Value("${workflow.role-runtime.claim-enabled:false}") boolean enabled) {
        this.enabled=enabled;this.controls=controls;this.killSwitch="DYNAMIC";
        this.enterpriseId=null;this.definitionId=null;this.definitionVersionId=null;this.nodeId=null;
    }

    public ConfiguredRoleClaimRuntimeGate(
            @Value("${workflow.role-runtime.claim-enabled:false}") boolean enabled,
            @Value("${workflow.role-runtime.kill-switch:STOP_NEW_AND_CLAIM}") String killSwitch,
            @Value("${workflow.role-runtime.canary.enterprise-id:}") String enterpriseId,
            @Value("${workflow.role-runtime.canary.definition-id:}") String definitionId,
            @Value("${workflow.role-runtime.canary.definition-version-id:}") String definitionVersionId,
            @Value("${workflow.role-runtime.canary.node-id:}") String nodeId) {
        this.enabled = enabled;
        this.killSwitch = killSwitch == null ? "" : killSwitch.trim();
        this.enterpriseId = parse(enterpriseId);
        this.definitionId = parse(definitionId);
        this.definitionVersionId = parse(definitionVersionId);
        this.nodeId = parse(nodeId);
        this.controls = null;
    }

    @Override public boolean allows(RoleClaimGateContext context) {
        if (controls != null) {
            if (!enabled) return false;
            Instant now=Instant.now();
            String enterprise="ENTERPRISE|"+context.enterpriseId();
            String definition="WORKFLOW_DEFINITION|"+context.enterpriseId()+"|DEF:"+context.definitionId();
            String version="DEFINITION_VERSION|"+context.enterpriseId()+"|DEF:"+context.definitionId()+"|VER:"+context.definitionVersionId();
            String canary="CANARY|"+context.enterpriseId()+"|DEF:"+context.definitionId()+"|VER:"+context.definitionVersionId()+"|NODE:"+context.nodeId();
            return decision("FEATURE_FLAG","GLOBAL","ON",now)&&decision("FEATURE_FLAG",enterprise,"ON",now)
                    &&decision("FEATURE_FLAG",definition,"ON",now)&&decision("CANARY",canary,"ALLOW",now)
                    &&decision("KILL_SWITCH","GLOBAL","ALLOW",now)&&decision("KILL_SWITCH",enterprise,"ALLOW",now)
                    &&decision("KILL_SWITCH",version,"ALLOW",now);
        }
        return enabled
                && "ALLOW".equals(killSwitch)
                && enterpriseId != null && enterpriseId == context.enterpriseId()
                && definitionId != null && definitionId == context.definitionId()
                && definitionVersionId != null && definitionVersionId == context.definitionVersionId()
                && nodeId != null && nodeId == context.nodeId();
    }

    private boolean decision(String type,String scope,String expected,Instant at){
        return controls.latest(type,scope,at).map(c->expected.equals(c.decision())).orElse(false);
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
