package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Single exact-scope runtime decision. Missing/ambiguous/stale controls fail closed. */
@Component
public final class ProductionCanaryRuntimeGate implements CanaryRuntimeGate {
    private final CanaryGovernanceRepository repository; private final RoleRuntimeGovernanceControlStore controls; private final boolean roleRuntimeEnabled;
    public ProductionCanaryRuntimeGate(CanaryGovernanceRepository repository,RoleRuntimeGovernanceControlStore controls,
            @Value("${workflow.role-runtime.claim-enabled:false}") boolean roleRuntimeEnabled){this.repository=repository;this.controls=controls;this.roleRuntimeEnabled=roleRuntimeEnabled;}
    @Override public boolean allows(CanaryScope scope,Instant at){
        try{
            if(!roleRuntimeEnabled)return false;
            var record=repository.latest(scope,at).orElse(null);
            if(record==null||record.state()!=CanaryGovernanceState.ENABLED)return false;
            return on("GLOBAL",at)&&on("ENTERPRISE|"+scope.enterpriseId(),at)
                    &&on("WORKFLOW_DEFINITION|"+scope.enterpriseId()+"|DEF:"+scope.definitionId(),at)
                    &&allow("GLOBAL",at)&&allow("ENTERPRISE|"+scope.enterpriseId(),at)
                    &&allow("DEFINITION_VERSION|"+scope.enterpriseId()+"|DEF:"+scope.definitionId()+"|VER:"+scope.definitionVersionId(),at);
        }catch(RuntimeException ex){return false;}
    }
    private boolean allow(String scope,Instant at){return controls.latest("KILL_SWITCH",scope,at).map(c->"ALLOW".equals(c.decision())).orElse(false);}
    private boolean on(String scope,Instant at){return controls.latest("FEATURE_FLAG",scope,at).map(c->"ON".equals(c.decision())).orElse(false);}
}
