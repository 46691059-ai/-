package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimCommitCapabilityGate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Re-reads DataScope, SoD and dynamic controls after row locks and immediately before Claim mutation. */
@Component
public final class ProductionRoleClaimCommitCapabilityGate implements RoleClaimCommitCapabilityGate {
    private final DataPermissionService dataScope; private final RoleRuntimeGovernanceControlStore controls;
    public ProductionRoleClaimCommitCapabilityGate(DataPermissionService dataScope,RoleRuntimeGovernanceControlStore controls){this.dataScope=dataScope;this.controls=controls;}
    @Override public Decision verify(Facts f){
        try{
            var p=dataScope.current();
            boolean scope=p.unrestricted()||p.allowedOrgIds().contains(f.initiatorOrgId())
                    ||(p.selfIncluded()&&Objects.equals(p.userId(),f.principal().userId()));
            if(!scope)return deny("DATASCOPE_CHANGED_OR_DENIED",f);
            if(f.initiatorUserId()==f.principal().userId())return deny("PLATFORM_SOD_INITIATOR_CONFLICT",f);
            String definition="DEF:"+f.definitionId();
            String version=definition+"|VER:"+f.definitionVersionId();
            String node=version+"|NODE:"+f.nodeId();
            String business=node+"|BUSINESS:"+f.businessType()+":"+f.businessKey();
            List<Key> required=List.of(
                    new Key("FEATURE_FLAG","GLOBAL","ON"),new Key("FEATURE_FLAG","ENTERPRISE|"+f.enterpriseId(),"ON"),
                    new Key("FEATURE_FLAG","WORKFLOW_DEFINITION|"+f.enterpriseId()+"|"+definition,"ON"),
                    new Key("CANARY","CANARY|"+f.enterpriseId()+"|"+node,"ALLOW"),
                    new Key("KILL_SWITCH","GLOBAL","ALLOW"),new Key("KILL_SWITCH","ENTERPRISE|"+f.enterpriseId(),"ALLOW"),
                    new Key("KILL_SWITCH","DEFINITION_VERSION|"+f.enterpriseId()+"|"+version,"ALLOW"),
                    new Key("BUSINESS_SOD","BUSINESS_SOD|"+f.enterpriseId()+"|"+business,"ALLOW"));
            StringBuilder evidence=new StringBuilder();
            for(Key key:required){var c=controls.latest(key.type,key.scope,f.checkedAt());if(c.isEmpty())return deny(key.type+"_UNAVAILABLE",f);
                if(!key.allowed.equals(c.get().decision()))return deny(key.type+"_CHANGED_OR_DENIED",f);
                evidence.append(key.type).append(':').append(key.scope).append(':').append(c.get().configVersion()).append(':').append(c.get().evidenceHash()).append('\n');}
            return new Decision(true,"ALLOW",ResolverContractHash.sha256(evidence.toString()).value());
        }catch(RuntimeException ex){return deny("COMMIT_CAPABILITY_INDETERMINATE",f);}
    }
    private Decision deny(String reason,Facts f){return new Decision(false,reason,ResolverContractHash.sha256(reason+"|"+f.instanceId()+"|"+f.principal().userId()).value());}
    private record Key(String type,String scope,String allowed){}
}
