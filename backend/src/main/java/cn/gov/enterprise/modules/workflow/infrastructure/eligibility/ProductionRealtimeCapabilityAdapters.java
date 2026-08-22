package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.*;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskActionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskActionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeCapabilityResult.Outcome.*;

/** Production adapters: no fallback and no candidate expansion. */
public final class ProductionRealtimeCapabilityAdapters {
    private ProductionRealtimeCapabilityAdapters() { }

    public static final class RoleMembership implements RealtimeEligibilityCapabilities.RealtimeRoleMembershipPort {
        private final RoleDirectoryPort directory;
        public RoleMembership(RoleDirectoryPort directory) { this.directory = Objects.requireNonNull(directory); }
        @Override public RealtimeRoleMembershipResult check(RealtimeEligibilityQuery q) {
            var result = directory.resolve(new RoleDirectoryQuery(q.enterpriseId(), q.organizationId(), q.roleCode(),
                    q.claimAt(), RoleDirectoryResolver.PORT_CONTRACT, q.correlationId()));
            boolean member = result.members().stream().filter(m -> m.effectiveAt(q.claimAt()))
                    .map(RoleDirectoryMember::userId).anyMatch(String.valueOf(q.candidateUserId())::equals);
            String evidence = hash("ROLE", result.resultHash(), String.valueOf(q.candidateUserId()), String.valueOf(member));
            return new RealtimeRoleMembershipResult(member ? PASS : DENY, result.complete(), false,
                    String.valueOf(result.revision()), result.resultHash(), result.contractHash(),
                    member ? "current role membership confirmed" : "candidate is not a current role member",
                    evidence, q.claimAt().plusSeconds(120));
        }
    }

    public static final class UserStatus implements RealtimeEligibilityCapabilities.RealtimeUserStatusPort {
        private final SysUserMapper users;
        public UserStatus(SysUserMapper users) { this.users = users; }
        @Override public RealtimeUserStatusResult check(RealtimeEligibilityQuery q) {
            SysUserEntity user = users.selectById(q.candidateUserId());
            boolean active = user != null && Integer.valueOf(1).equals(user.getStatus());
            return new RealtimeUserStatusResult(active ? RealtimeUserStatus.ACTIVE : RealtimeUserStatus.DISABLED,
                    "SYS_USER_STATUS_V1", hash("USER", String.valueOf(q.candidateUserId()), String.valueOf(active)));
        }
    }

    public static final class OrganizationMembership implements RealtimeEligibilityCapabilities.RealtimeOrganizationMembershipPort {
        private final SysUserMapper users; private final SysOrgMapper orgs;
        public OrganizationMembership(SysUserMapper users, SysOrgMapper orgs) { this.users=users; this.orgs=orgs; }
        @Override public RealtimeCapabilityResult checkExactBusinessOrganization(RealtimeEligibilityQuery q) {
            try {
                long orgId=Long.parseLong(q.organizationId()); SysUserEntity user=users.selectById(q.candidateUserId());
                SysOrgEntity org=orgs.selectById(orgId);
                boolean pass=user!=null && Objects.equals(user.getOrgId(),orgId) && org!=null && Integer.valueOf(1).equals(org.getStatus());
                return result(pass ? PASS : DENY, pass ? "exact active organization membership confirmed" : "organization membership lost",
                        "ORG_MEMBERSHIP_V1", q.claimAt(), q.organizationId(), String.valueOf(q.candidateUserId()));
            } catch (RuntimeException ex) { return result(INDETERMINATE,"organization evidence unavailable","ORG_MEMBERSHIP_V1",q.claimAt(),q.organizationId()); }
        }
    }

    public static final class DataScope implements RealtimeEligibilityCapabilities.RoleRuntimeDataScopePort {
        private final DataPermissionService permissions;
        public DataScope(DataPermissionService permissions) { this.permissions=permissions; }
        @Override public RealtimeCapabilityResult check(RealtimeEligibilityQuery q) {
            try {
                DataPermissionContext c=permissions.current(); long org=Long.parseLong(q.organizationId());
                if (!Objects.equals(c.userId(),q.candidateUserId())) return result(DENY,"principal/candidate mismatch","DATASCOPE_POLICY_V1",q.claimAt(),q.correlationId());
                boolean pass=c.unrestricted() || c.allowedOrgIds().contains(org) || (c.selfIncluded() && Objects.equals(c.orgId(),org));
                return result(pass?PASS:DENY,pass?"data scope allows business organization":"outside data scope",
                        "DATASCOPE_POLICY_V1",q.claimAt(),String.valueOf(c.userId()),String.valueOf(org),c.dataScope().name());
            } catch (RuntimeException ex) { return result(INDETERMINATE,"data scope unavailable","DATASCOPE_POLICY_V1",q.claimAt(),q.correlationId()); }
        }
    }

    public static final class PlatformSoD implements RealtimeEligibilityCapabilities.PlatformSoDPort {
        private final WorkflowInstanceMapper instances; private final WorkflowTaskActionMapper actions;
        private final RoleRuntimeGovernanceControlStore controls;
        public PlatformSoD(WorkflowInstanceMapper instances, WorkflowTaskActionMapper actions,
                RoleRuntimeGovernanceControlStore controls) {
            this.instances=instances; this.actions=actions; this.controls=controls;
        }
        @Override public RealtimeCapabilityResult check(RealtimeEligibilityQuery q) {
            try {
                WorkflowInstanceEntity instance=instances.selectById(q.workflowInstanceId());
                if(instance==null) return result(INDETERMINATE,"instance unavailable","PLATFORM_SOD_V1",q.claimAt(),q.correlationId());
                if(Objects.equals(instance.getInitiatorUserId(),q.candidateUserId())) return result(DENY,"initiator cannot approve own instance","PLATFORM_SOD_V1",q.claimAt(),"INITIATOR",String.valueOf(q.candidateUserId()));
                List<WorkflowTaskActionEntity> prior=actions.selectList(new LambdaQueryWrapper<WorkflowTaskActionEntity>()
                        .eq(WorkflowTaskActionEntity::getInstanceId,q.workflowInstanceId())
                        .eq(WorkflowTaskActionEntity::getOperatorUserId,q.candidateUserId())
                        .in(WorkflowTaskActionEntity::getActionType,List.of("APPROVE","REJECT","WITHDRAW")));
                boolean pass=prior.isEmpty();
                if (!pass) return result(DENY,"same actor already performed critical action",
                        "PLATFORM_SOD_V1",q.claimAt(),String.valueOf(q.workflowInstanceId()),String.valueOf(q.candidateUserId()),String.valueOf(prior.size()));

                // These scopes are candidate-specific decisions produced by the versioned governance provider.
                // Missing, ambiguous, expired or non-ALLOW evidence fails closed.
                List<String> scopes=List.of(
                        "ORG_INCOMPATIBLE|"+q.enterpriseId()+"|"+q.businessScopeReference()+"|"+q.organizationId()+"|"+q.nodeExecutionId(),
                        "GOVERNANCE_ADMIN_SELF_APPROVE|"+q.enterpriseId()+"|"+q.businessScopeReference()+"|"+q.roleCode()+"|"+q.candidateUserId()+"|"+q.workflowInstanceId(),
                        "SAME_ACTOR_FORBIDDEN|"+q.enterpriseId()+"|"+q.businessScopeReference()+"|"+q.candidateUserId()+"|"+q.workflowInstanceId());
                var governed=scopes.stream().map(scope->controls.latest("PLATFORM_SOD",scope,q.claimAt())).toList();
                if(governed.stream().anyMatch(java.util.Optional::isEmpty))
                    return result(INDETERMINATE,"platform SoD governance evidence missing or ambiguous",
                            "PLATFORM_SOD_V2",q.claimAt(),String.join(";",scopes));
                var facts=governed.stream().map(java.util.Optional::get).toList();
                boolean governedPass=facts.stream().allMatch(c->"ALLOW".equals(c.decision()));
                String evidence=hash("PLATFORM_SOD_V2",facts.stream()
                        .map(c->c.configVersion()+":"+c.evidenceHash()).sorted().reduce("",(a,b)->a+"|"+b));
                long maxVersion=facts.stream().mapToLong(RoleRuntimeGovernanceControlStore.Control::configVersion).max().orElseThrow();
                Instant until=facts.stream().map(RoleRuntimeGovernanceControlStore.Control::effectiveTo)
                        .filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
                return new RealtimeCapabilityResult(governedPass?PASS:DENY,
                        governedPass?"platform separation of duties passed":"versioned platform SoD rule denied execution",
                        evidence,"PLATFORM_SOD_CONFIG_"+maxVersion,until);
            } catch(RuntimeException ex){return result(INDETERMINATE,"platform SoD unavailable","PLATFORM_SOD_V1",q.claimAt(),q.correlationId());}
        }
    }

    public static final class GovernedCapability implements RealtimeEligibilityCapabilities.BusinessSoDPort,
            RealtimeEligibilityCapabilities.RealtimeFeatureFlagPort,
            RealtimeEligibilityCapabilities.RealtimeKillSwitchPort,
            RealtimeEligibilityCapabilities.RealtimeCanaryPort {
        public enum Kind { BUSINESS_SOD, FEATURE_FLAG, KILL_SWITCH, CANARY }
        private final RoleRuntimeGovernanceControlStore store; private final Kind kind;
        public GovernedCapability(RoleRuntimeGovernanceControlStore store, Kind kind){this.store=store;this.kind=kind;}
        @Override public RealtimeCapabilityResult check(RealtimeEligibilityQuery q) {
            List<String> scopes = switch(kind) {
                case FEATURE_FLAG -> List.of("GLOBAL","ENTERPRISE|"+q.enterpriseId(),
                        "WORKFLOW_DEFINITION|"+q.enterpriseId()+"|"+q.businessScopeReference());
                case KILL_SWITCH -> List.of("GLOBAL","ENTERPRISE|"+q.enterpriseId(),
                        "DEFINITION_VERSION|"+q.enterpriseId()+"|"+q.businessScopeReference());
                case CANARY -> List.of("CANARY|"+q.enterpriseId()+"|"+q.businessScopeReference());
                case BUSINESS_SOD -> List.of("BUSINESS_SOD|"+q.enterpriseId()+"|"+q.businessScopeReference());
            };
            var controls=scopes.stream().map(scope->store.latest(kind.name(),scope,q.claimAt())).toList();
            if(controls.stream().anyMatch(java.util.Optional::isEmpty))
                return result(INDETERMINATE,kind+" control missing or ambiguous",kind+"_V1",q.claimAt(),String.join(";",scopes));
            var found=controls.stream().map(java.util.Optional::get).toList();
            boolean pass=found.stream().allMatch(c->switch(kind){
                case FEATURE_FLAG -> "ON".equals(c.decision());
                case KILL_SWITCH -> "ALLOW".equals(c.decision());
                default -> "ALLOW".equals(c.decision());
            });
            String evidence=hash(kind.name(),found.stream().map(c->c.configVersion()+":"+c.evidenceHash()).sorted().reduce("",(a,b)->a+"|"+b));
            long maxVersion=found.stream().mapToLong(RoleRuntimeGovernanceControlStore.Control::configVersion).max().orElseThrow();
            Instant until=found.stream().map(RoleRuntimeGovernanceControlStore.Control::effectiveTo).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
            return new RealtimeCapabilityResult(pass?PASS:DENY,pass?kind+" allows execution":kind+" denies execution",
                    evidence,kind+"_CONFIG_"+maxVersion,until);
        }
    }

    public static final class AuditAvailability implements RealtimeEligibilityCapabilities.RealtimeAuditCapabilityPort {
        private final ExternalAuditOutboxWriter outbox;
        public AuditAvailability(ExternalAuditOutboxWriter outbox){this.outbox=outbox;}
        @Override public RealtimeCapabilityResult check(RealtimeEligibilityQuery q){
            boolean ready=outbox.ready();
            return result(ready?PASS:INDETERMINATE,ready?"transactional external audit outbox ready":"external audit outbox unavailable",
                    "EXTERNAL_AUDIT_OUTBOX_V1",q.claimAt(),q.correlationId(),String.valueOf(ready));
        }
    }

    private static RealtimeCapabilityResult result(RealtimeCapabilityResult.Outcome o,String reason,String version,Instant at,String... facts){
        return new RealtimeCapabilityResult(o,reason,hash(version,reason,String.join("|",facts)),version,at.plusSeconds(120));
    }
    static String hash(String... values){ return ResolverContractHash.sha256(String.join("\n",values)).value(); }
}
