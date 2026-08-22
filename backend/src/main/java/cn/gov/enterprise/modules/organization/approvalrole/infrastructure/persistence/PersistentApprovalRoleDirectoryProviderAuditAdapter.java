package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import cn.gov.enterprise.modules.organization.approvalrole.domain.audit.ApprovalRoleDirectoryProviderAuditEvidence;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleDirectoryProviderAuditPort;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.ApprovalRoleDirectoryProviderAuditEntity;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper.ApprovalRoleDirectoryProviderAuditMapper;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="app.approval-role-directory.provider",name="enabled",havingValue="true")
public class PersistentApprovalRoleDirectoryProviderAuditAdapter implements ApprovalRoleDirectoryProviderAuditPort {
    private final ApprovalRoleDirectoryProviderAuditMapper mapper;
    public PersistentApprovalRoleDirectoryProviderAuditAdapter(ApprovalRoleDirectoryProviderAuditMapper mapper){this.mapper=mapper;}
    @Override public void append(ApprovalRoleDirectoryProviderAuditEvidence evidence){
        try { if(mapper.insert(toEntity(evidence))!=1) throw new AuditPersistenceException("PROVIDER_AUDIT_INSERT_NOT_ACKNOWLEDGED"); }
        catch(AuditPersistenceException failure){throw failure;}
        catch(RuntimeException failure){throw new AuditPersistenceException("PROVIDER_AUDIT_PERSISTENCE_FAILED",failure);}
    }
    @Override public List<ApprovalRoleDirectoryProviderAuditEvidence> findRecent(int limit){
        if(limit<1||limit>1000)throw new IllegalArgumentException("limit must be between 1 and 1000");
        return mapper.selectRecent(limit).stream().map(PersistentApprovalRoleDirectoryProviderAuditAdapter::toDomain).toList();
    }
    private static ApprovalRoleDirectoryProviderAuditEntity toEntity(ApprovalRoleDirectoryProviderAuditEvidence e){
        var x=new ApprovalRoleDirectoryProviderAuditEntity();x.setAuditId(e.auditId());x.setAuditEventId(e.auditEventId());x.setCorrelationId(e.correlationId());x.setRequestId(e.requestId());x.setProviderCode(e.providerCode());x.setProviderVersion(e.providerVersion());x.setServiceIdentity(e.serviceIdentity());x.setEnvironmentIdentity(e.environmentIdentity());x.setCallerServiceIdentity(e.callerServiceIdentity());x.setEnterpriseId(e.enterpriseId());x.setOrganizationId(e.organizationId());x.setRoleCode(e.roleCode());x.setEffectiveAt(local(e.effectiveAt()));x.setContractVersion(e.contractVersion());x.setContractHash(e.contractHash());x.setCanonicalVersion(e.canonicalVersion());x.setDirectoryRevision(e.directoryRevision());x.setDirectoryResultHash(e.directoryResultHash());x.setCandidateCount(e.candidateCount());x.setOutcome(e.outcome().name());x.setFailureCode(e.failureCode());x.setRequestHash(e.requestHash());x.setEvidenceHash(e.evidenceHash());x.setStartedAt(local(e.startedAt()));x.setCompletedAt(local(e.completedAt()));x.setRetentionPolicy("NOT_YET_PRODUCTION_APPROVED");return x;
    }
    private static ApprovalRoleDirectoryProviderAuditEvidence toDomain(ApprovalRoleDirectoryProviderAuditEntity e){return new ApprovalRoleDirectoryProviderAuditEvidence(e.getAuditId(),e.getAuditEventId(),e.getCorrelationId(),e.getRequestId(),e.getProviderCode(),e.getProviderVersion(),e.getServiceIdentity(),e.getEnvironmentIdentity(),e.getCallerServiceIdentity(),e.getEnterpriseId(),e.getOrganizationId(),e.getRoleCode(),instant(e.getEffectiveAt()),e.getContractVersion(),e.getContractHash(),e.getCanonicalVersion(),e.getDirectoryRevision(),e.getDirectoryResultHash(),e.getCandidateCount(),ApprovalRoleDirectoryProviderAuditEvidence.Outcome.valueOf(e.getOutcome()),e.getFailureCode(),e.getRequestHash(),e.getEvidenceHash(),instant(e.getStartedAt()),instant(e.getCompletedAt()));}
    private static java.time.LocalDateTime local(java.time.Instant v){return v==null?null:java.time.LocalDateTime.ofInstant(v,ZoneOffset.UTC);}
    private static java.time.Instant instant(java.time.LocalDateTime v){return v==null?null:v.toInstant(ZoneOffset.UTC);}
    public static final class AuditPersistenceException extends RuntimeException { public AuditPersistenceException(String m){super(m);}public AuditPersistenceException(String m,Throwable c){super(m,c);} }
}
