package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleAssignmentRepository;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper.ApprovalRoleAssignmentMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRoleAssignmentRepositoryImpl implements ApprovalRoleAssignmentRepository {
    private final ApprovalRoleAssignmentMapper mapper;
    public ApprovalRoleAssignmentRepositoryImpl(ApprovalRoleAssignmentMapper mapper){this.mapper=mapper;}
    public void insert(ApprovalRoleAssignment d){mapper.insert(ApprovalRoleEntityMapping.toEntity(d));}
    public Optional<ApprovalRoleAssignment> findByIdForUpdate(long id){return Optional.ofNullable(mapper.selectByIdForUpdate(id)).map(ApprovalRoleEntityMapping::toDomain);}
    public List<ApprovalRoleAssignment> findEffective(String enterprise,long org,ApprovalRoleCode code,Instant at){return mapper.selectEffective(enterprise,org,code.value(),ApprovalRoleEntityMapping.db(at)).stream().map(ApprovalRoleEntityMapping::toDomain).toList();}
    public List<ApprovalRoleAssignment> lockOverlapping(String enterprise,long org,long role,long user,String sourceSystem,String sourceReference,Instant from,Instant to){return mapper.selectOverlappingForUpdate(enterprise,org,role,user,sourceSystem,sourceReference,ApprovalRoleEntityMapping.db(from),ApprovalRoleEntityMapping.db(to)).stream().map(ApprovalRoleEntityMapping::toDomain).toList();}
    public List<ApprovalRoleAssignment> history(String enterprise,long org,ApprovalRoleCode code){return mapper.selectHistory(enterprise,org,code.value()).stream().map(ApprovalRoleEntityMapping::toDomain).toList();}
    public boolean update(ApprovalRoleAssignment d,int expected){return mapper.updateGoverned(d.id(),ApprovalRoleEntityMapping.db(d.effectiveTo()),d.status().name(),d.updatedBy(),ApprovalRoleEntityMapping.db(d.updatedTime()),expected)==1;}
}
