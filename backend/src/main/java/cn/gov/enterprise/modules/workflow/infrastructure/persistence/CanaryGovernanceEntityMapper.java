package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.CanaryGovernanceEntity;
import java.time.*;

final class CanaryGovernanceEntityMapper {
    private CanaryGovernanceEntityMapper() { }
    static CanaryGovernanceEntity toEntity(CanaryGovernanceRecord r) {
        CanaryGovernanceEntity e=new CanaryGovernanceEntity();
        e.setId(r.id());e.setPreviousRecordId(r.previousRecordId());
        e.setEnterpriseId(r.scope().enterpriseId());e.setOrganizationId(r.scope().organizationId());
        e.setDefinitionId(r.scope().definitionId());e.setDefinitionVersionId(r.scope().definitionVersionId());
        e.setNodeId(r.scope().nodeId());e.setRoleCode(r.scope().roleCode());
        e.setGovernanceState(r.state().name());e.setGovernanceRevision(r.revision());
        var a=r.evidence();e.setDirectoryRevision(a.directoryRevision());e.setDirectoryCandidateCount(a.directoryCandidateCount());
        e.setDirectoryResultHash(a.directoryResultHash());e.setVersionBindingHash(a.versionBindingHash());
        e.setManifestHash(a.manifestHash());e.setContentHash(a.contentHash());e.setReleaseTag(a.releaseTag());
        e.setReleaseCommit(a.releaseCommit());e.setStructuralFingerprint(a.structuralFingerprint());
        e.setApprovalActor(r.approvalActor());e.setApprovedAt(local(r.approvedAt()));
        e.setEnablementActor(r.enablementActor());e.setEnabledAt(local(r.enabledAt()));
        e.setSuspendedAt(local(r.suspendedAt()));e.setRevokedAt(local(r.revokedAt()));
        e.setReason(r.reason());e.setEffectiveFrom(local(r.effectiveFrom()));e.setEffectiveTo(local(r.effectiveTo()));
        return e;
    }
    static CanaryGovernanceRecord toDomain(CanaryGovernanceEntity e) {
        var scope=new CanaryScope(e.getEnterpriseId(),e.getOrganizationId(),e.getDefinitionId(),e.getDefinitionVersionId(),e.getNodeId(),e.getRoleCode());
        var evidence=new CanaryApprovalEvidence(e.getDirectoryRevision(),e.getDirectoryCandidateCount(),e.getDirectoryResultHash(),
                e.getVersionBindingHash(),e.getManifestHash(),e.getContentHash(),e.getReleaseTag(),e.getReleaseCommit(),e.getStructuralFingerprint());
        return new CanaryGovernanceRecord(e.getId(),e.getPreviousRecordId(),scope,CanaryGovernanceState.valueOf(e.getGovernanceState()),
                e.getGovernanceRevision(),evidence,e.getApprovalActor(),instant(e.getApprovedAt()),e.getEnablementActor(),
                instant(e.getEnabledAt()),instant(e.getSuspendedAt()),instant(e.getRevokedAt()),e.getReason(),
                instant(e.getEffectiveFrom()),instant(e.getEffectiveTo()));
    }
    private static LocalDateTime local(Instant v){return v==null?null:LocalDateTime.ofInstant(v,ZoneOffset.UTC);}
    private static Instant instant(LocalDateTime v){return v==null?null:v.toInstant(ZoneOffset.UTC);}
}
