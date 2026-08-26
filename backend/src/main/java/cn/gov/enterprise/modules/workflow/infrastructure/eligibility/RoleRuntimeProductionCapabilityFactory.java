package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityCapabilities;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RoleRuntimeProductionCapabilityBundle;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskActionMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Single construction point for the production capability bundle. It never changes Registry state. */
@Component
public final class RoleRuntimeProductionCapabilityFactory {
    private final DataPermissionService dataScope; private final SysUserMapper users; private final SysOrgMapper orgs;
    private final WorkflowInstanceMapper instances; private final WorkflowTaskActionMapper actions;
    private final RoleRuntimeGovernanceControlStore controls; private final ExternalAuditOutboxWriter audit;
    private final cn.gov.enterprise.modules.workflow.domain.canary.CanaryRuntimeGate canary;
    private final RoleRuntimeCapabilityMetrics metrics;
    public RoleRuntimeProductionCapabilityFactory(DataPermissionService dataScope,SysUserMapper users,SysOrgMapper orgs,
            WorkflowInstanceMapper instances,WorkflowTaskActionMapper actions,
            RoleRuntimeGovernanceControlStore controls,ExternalAuditOutboxWriter audit,RoleRuntimeCapabilityMetrics metrics,
            cn.gov.enterprise.modules.workflow.domain.canary.CanaryRuntimeGate canary){
        this.dataScope=dataScope;this.users=users;this.orgs=orgs;this.instances=instances;this.actions=actions;this.controls=controls;this.audit=audit;this.metrics=metrics;this.canary=canary;
    }

    public RoleRuntimeProductionCapabilityBundle create(RoleDirectoryPort productionDirectory){
        Objects.requireNonNull(productionDirectory,"productionDirectory");
        String type=productionDirectory.getClass().getName().toLowerCase();
        if(type.contains("fake")||type.contains("inmemory")||type.contains("stub"))
            throw new IllegalArgumentException("Fake Directory is forbidden in production capability bundle");
        var governedBusiness=new ProductionRealtimeCapabilityAdapters.GovernedCapability(controls,
                ProductionRealtimeCapabilityAdapters.GovernedCapability.Kind.BUSINESS_SOD);
        var feature=new ProductionRealtimeCapabilityAdapters.GovernedCapability(controls,
                ProductionRealtimeCapabilityAdapters.GovernedCapability.Kind.FEATURE_FLAG);
        var kill=new ProductionRealtimeCapabilityAdapters.GovernedCapability(controls,
                ProductionRealtimeCapabilityAdapters.GovernedCapability.Kind.KILL_SWITCH);
        var canary=new ProductionRealtimeCapabilityAdapters.GovernedCapability(controls,
                ProductionRealtimeCapabilityAdapters.GovernedCapability.Kind.CANARY,this.canary);
        var role=new ProductionRealtimeCapabilityAdapters.RoleMembership(productionDirectory);
        var user=new ProductionRealtimeCapabilityAdapters.UserStatus(users);
        var org=new ProductionRealtimeCapabilityAdapters.OrganizationMembership(users,orgs);
        var scope=new ProductionRealtimeCapabilityAdapters.DataScope(dataScope);
        var platform=new ProductionRealtimeCapabilityAdapters.PlatformSoD(instances,actions,controls);
        var auditCapability=new ProductionRealtimeCapabilityAdapters.AuditAvailability(audit);
        return new RoleRuntimeProductionCapabilityBundle(new RealtimeEligibilityCapabilities.Bundle(
                q->metrics.observe("role_directory",()->role.check(q),r->r.outcome().name()),
                q->metrics.observe("user_status",()->user.check(q),r->r.status().name()),
                q->metrics.observe("organization",()->org.checkExactBusinessOrganization(q),r->r.outcome().name()),
                q->metrics.observe("datascope",()->scope.check(q),r->r.outcome().name()),
                q->metrics.observe("platform_sod",()->platform.check(q),r->r.outcome().name()),
                q->metrics.observe("business_sod",()->governedBusiness.check(q),r->r.outcome().name()),
                q->metrics.observe("external_audit",()->auditCapability.check(q),r->r.outcome().name()),
                q->metrics.observe("feature_flag",()->feature.check(q),r->r.outcome().name()),
                q->metrics.observe("kill_switch",()->kill.check(q),r->r.outcome().name()),
                q->metrics.observe("canary",()->canary.check(q),r->r.outcome().name())));
    }
}
