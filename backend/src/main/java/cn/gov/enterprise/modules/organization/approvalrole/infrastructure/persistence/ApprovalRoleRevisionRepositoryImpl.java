package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleRevisionRepository;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.entity.*;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRoleRevisionRepositoryImpl implements ApprovalRoleRevisionRepository {
    private final ApprovalRoleRevisionHeadMapper heads; private final ApprovalRoleRevisionMapper revisions;
    public ApprovalRoleRevisionRepositoryImpl(ApprovalRoleRevisionHeadMapper heads,ApprovalRoleRevisionMapper revisions){this.heads=heads;this.revisions=revisions;}
    public void insertHead(ApprovalRoleRevisionHead d,String actor){var e=new ApprovalRoleRevisionHeadEntity();ApprovalRoleEntityMapping.audit(e,d.id(),actor,Instant.now(),actor,Instant.now(),d.version(),0);e.setEnterpriseId(d.enterpriseId());e.setOrganizationId(d.organizationId());e.setRoleCode(d.roleCode().value());e.setCurrentRevision(d.currentRevision());e.setCurrentResultHash(d.currentResultHash());heads.insert(e);}
    public Optional<ApprovalRoleRevisionHead> lockHead(String enterprise,long org,ApprovalRoleCode code){return Optional.ofNullable(heads.selectForUpdate(enterprise,org,code.value())).map(ApprovalRoleEntityMapping::toDomain);}
    public Optional<ApprovalRoleRevisionHead> current(String enterprise,long org,ApprovalRoleCode code){return Optional.ofNullable(heads.selectCurrent(enterprise,org,code.value())).map(ApprovalRoleEntityMapping::toDomain);}
    public boolean advance(ApprovalRoleRevisionHead d,long next,String hash,String actor){return heads.advance(d.id(),d.currentRevision(),next,hash,actor,d.version())==1;}
    public void append(ApprovalRoleRevision d){var e=new ApprovalRoleRevisionEntity();ApprovalRoleEntityMapping.audit(e,d.id(),d.publishedBy(),d.createdTime(),d.publishedBy(),d.createdTime(),0,d.deleteToken());e.setEnterpriseId(d.enterpriseId());e.setOrganizationId(d.organizationId());e.setRoleCode(d.roleCode().value());e.setRevision(d.revision());e.setResultHash(d.resultHash());e.setChangeType(d.changeType());e.setChangeReason(d.changeReason());e.setEffectiveFrom(ApprovalRoleEntityMapping.db(d.effectiveFrom()));e.setAffectedFrom(ApprovalRoleEntityMapping.db(d.affectedFrom()));e.setAffectedTo(ApprovalRoleEntityMapping.db(d.affectedTo()));e.setCorrectionReference(d.correctionReference());e.setPublishedAt(ApprovalRoleEntityMapping.db(d.publishedAt()));e.setPublishedBy(d.publishedBy());e.setPreviousRevision(d.previousRevision());e.setPreviousResultHash(d.previousResultHash());revisions.insert(e);}
    public List<ApprovalRoleRevision> history(String enterprise,long org,ApprovalRoleCode code){return revisions.selectHistory(enterprise,org,code.value()).stream().map(ApprovalRoleEntityMapping::toDomain).toList();}
    public List<ApprovalRoleRevisionHead> listByRole(String enterprise,ApprovalRoleCode code){return heads.selectByRole(enterprise,code.value()).stream().map(ApprovalRoleEntityMapping::toDomain).toList();}
}
