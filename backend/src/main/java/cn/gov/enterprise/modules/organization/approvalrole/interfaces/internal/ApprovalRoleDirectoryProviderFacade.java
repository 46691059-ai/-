package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleDirectoryProviderAuditPort;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.workflow.OrganizationApprovalRoleDirectoryAdapter;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.PersistentApprovalRoleDirectoryProviderAuditAdapter.AuditPersistenceException;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix="app.approval-role-directory.provider", name="enabled", havingValue="true")
public final class ApprovalRoleDirectoryProviderFacade {
    public static final String SOURCE = "ORGANIZATION_APPROVAL_ROLE_DIRECTORY_V1";
    private static final Instant VECTOR_AT = Instant.parse("2026-05-31T12:00:00Z");
    private final ApprovalRoleDirectoryService directory;
    private final OrganizationApprovalRoleDirectoryAdapter contractAdapter;
    private final ApprovalRoleDirectoryProviderProperties properties;
    private final ApprovalRoleDirectoryProviderAuditPort audit;
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    public ApprovalRoleDirectoryProviderFacade(ApprovalRoleDirectoryService directory,
            ApprovalRoleDirectoryProviderProperties properties,
            ApprovalRoleDirectoryProviderAuditPort audit, JdbcTemplate jdbc, MeterRegistry metrics) {
        properties.validateEnabled(); this.directory=directory; this.contractAdapter=new OrganizationApprovalRoleDirectoryAdapter(directory);
        this.properties=properties; this.audit=audit; this.jdbc=jdbc; this.metrics=metrics;
    }

    public ApprovalRoleDirectoryProviderDtos.Health health() {
        boolean db=false,schema=false;
        try {
            db=jdbc.queryForObject("SELECT 1",Integer.class)==1;
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('approval_role','approval_role_assignment','approval_role_revision_head','approval_role_revision')",Integer.class);
            schema=count!=null&&count==4;
        } catch(RuntimeException ignored) { db=false; schema=false; }
        return new ApprovalRoleDirectoryProviderDtos.Health(db&&schema?"UP":"DOWN",true,db,schema,Instant.now());
    }

    public ApprovalRoleDirectoryProviderDtos.Metadata metadata() {
        return new ApprovalRoleDirectoryProviderDtos.Metadata(properties.getProviderCode(),properties.getProviderVersion(),
                properties.getServiceIdentity(),properties.getEnvironmentIdentity(),properties.contractVersion(),properties.contractHash(),
                properties.canonicalVersion(),canonicalVectorHash(),Set.of("AGGREGATE_REVISION","HISTORICAL_EFFECTIVE_AT","COMPLETE_RESULT","NO_PAGINATION"),
                "ROLE_ORG_MONOTONIC_REVISION","true".equals("true"),"AUTHORITATIVE_FULL_SET",false);
    }

    public ApprovalRoleDirectoryProviderDtos.CanonicalVerifyResult verifyCanonical(String expectedHash) {
        String actual=canonicalVectorHash();
        return new ApprovalRoleDirectoryProviderDtos.CanonicalVerifyResult(properties.canonicalVersion(),actual,actual.equals(expectedHash));
    }

    public ApprovalRoleDirectoryProviderDtos.ResolveResult resolve(ApprovalRoleDirectoryProviderDtos.ResolveRequest request) {
        return resolve(request,properties.getAcceptedClientIdentity(),request==null?null:request.correlationId());
    }

    public ApprovalRoleDirectoryProviderDtos.ResolveResult resolve(ApprovalRoleDirectoryProviderDtos.ResolveRequest request,
            String callerServiceIdentity,String requestId) {
        long startedNanos=System.nanoTime(); Instant startedAt=Instant.now();
        String correlation=request==null?null:safe(request.correlationId(),100);
        String stableRequestId=safe(requestId,100);
        if(stableRequestId==null)stableRequestId=correlation==null?java.util.UUID.randomUUID().toString():correlation;
        try {
            if(request==null)throw new IllegalArgumentException("request is required");
            correlation=required(request.correlationId(),"correlationId");
            RoleDirectoryResult result=contractAdapter.resolve(new RoleDirectoryQuery(request.enterpriseId(),request.organizationId(),
                    request.roleCode(),request.effectiveAt(),ApprovalRoleDirectoryResult.CONTRACT_VERSION,correlation));
            List<ApprovalRoleDirectoryProviderDtos.Member> members=result.members().stream().map(m->new ApprovalRoleDirectoryProviderDtos.Member(
                    m.userId(),m.assignmentId(),m.roleCode(),m.organizationId(),m.effectiveFrom(),m.effectiveTo(),m.sourceType().name(),m.sourceRef(),m.revision())).toList();
            long elapsed=System.nanoTime()-startedNanos; Instant completedAt=Instant.now();
            audit.append(ApprovalRoleDirectoryProviderAuditEvidence.create(correlation,stableRequestId,
                    properties.getProviderCode(),properties.getProviderVersion(),properties.getServiceIdentity(),
                    properties.getEnvironmentIdentity(),safe(callerServiceIdentity,100),safe(request.enterpriseId(),100),
                    safe(request.organizationId(),100),safe(request.roleCode(),100),request.effectiveAt(),
                    properties.contractVersion(),result.contractHash(),properties.canonicalVersion(),result.revision(),result.resultHash(),
                    (int)members.stream().map(ApprovalRoleDirectoryProviderDtos.Member::userId).distinct().count(),
                    ApprovalRoleDirectoryProviderAuditEvidence.Outcome.SUCCESS,null,startedAt,completedAt));
            record("SUCCESS",members.size(),elapsed);
            return new ApprovalRoleDirectoryProviderDtos.ResolveResult(request.enterpriseId(),result.organizationId(),result.roleCode(),result.effectiveAt(),
                    result.revision(),result.complete(),members,result.resultHash(),ApprovalRoleDirectoryResult.CONTRACT_VERSION,result.contractHash(),
                    ApprovalRoleDirectoryResult.CANONICAL_VERSION,properties.getProviderCode(),properties.getProviderVersion(),properties.getEnvironmentIdentity(),Instant.now(),SOURCE);
        } catch(AuditPersistenceException failure) {
            record("AUDIT_PERSISTENCE_FAILED",0,System.nanoTime()-startedNanos);
            throw failure;
        } catch(RuntimeException failure) {
            long elapsed=System.nanoTime()-startedNanos; record(failure.getClass().getSimpleName(),0,elapsed);
            var outcome=(failure instanceof IllegalArgumentException||failure instanceof ApprovalRoleDirectoryFailure)
                    ?ApprovalRoleDirectoryProviderAuditEvidence.Outcome.REJECTED:ApprovalRoleDirectoryProviderAuditEvidence.Outcome.FAILED;
            String code=failure instanceof ApprovalRoleDirectoryFailure d?d.code().name():failure.getClass().getSimpleName();
            try {
                audit.append(ApprovalRoleDirectoryProviderAuditEvidence.create(correlation,stableRequestId,
                        properties.getProviderCode(),properties.getProviderVersion(),properties.getServiceIdentity(),
                        properties.getEnvironmentIdentity(),safe(callerServiceIdentity,100),safe(request==null?null:request.enterpriseId(),100),
                        safe(request==null?null:request.organizationId(),100),safeCode(request==null?null:request.roleCode()),
                        request==null?null:request.effectiveAt(),properties.contractVersion(),properties.contractHash(),properties.canonicalVersion(),
                        null,null,null,outcome,code,startedAt,Instant.now()));
            } catch(AuditPersistenceException auditFailure) {
                auditFailure.addSuppressed(failure); throw auditFailure;
            }
            throw failure;
        }
    }

    public List<ApprovalRoleDirectoryProviderAuditEvidence> auditSnapshot() { return audit.findRecent(1000); }

    public static String canonicalVectorHash() {
        ApprovalRoleAssignmentSource source=new ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,
                "TEST_VECTOR","VECTOR-DECISION-1",ApprovalRoleCanonical.sha256("VECTOR-DECISION-1"),1,Instant.parse("2026-01-01T00:00:00Z"));
        var member=new ApprovalRoleDirectoryMember(990101,List.of(new ApprovalRoleDirectoryMember.Evidence(990201,
                Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-06-01T00:00:00Z"),source)));
        return ApprovalRoleDirectoryResult.complete("TEST_ENTERPRISE",990001,new ApprovalRoleCode("TEST_APPROVER"),VECTOR_AT,7,List.of(member),VECTOR_AT).resultHash();
    }

    private void record(String outcome,int candidates,long nanos){
        metrics.counter("approval.role.directory.requests","outcome",outcome).increment();
        metrics.timer("approval.role.directory.latency","outcome",outcome).record(nanos,TimeUnit.NANOSECONDS);
        metrics.summary("approval.role.directory.candidates").record(candidates);
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String safe(String value,int max){if(value==null||value.isBlank())return null;String v=value.trim();return v.length()<=max&&v.matches("[\\p{L}\\p{N}_.:@/-]+")?v:null;}
    private static String safeCode(String value){String v=safe(value,100);return v!=null&&v.matches("[A-Z][A-Z0-9_]{2,99}")?v:null;}
}
