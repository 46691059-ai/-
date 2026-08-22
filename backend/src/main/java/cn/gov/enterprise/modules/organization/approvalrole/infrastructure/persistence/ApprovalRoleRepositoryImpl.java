package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleRepository;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.mapper.ApprovalRoleMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRoleRepositoryImpl implements ApprovalRoleRepository {
    private final ApprovalRoleMapper mapper;
    public ApprovalRoleRepositoryImpl(ApprovalRoleMapper mapper){this.mapper=mapper;}
    public void insert(ApprovalRole role){mapper.insert(ApprovalRoleEntityMapping.toEntity(role));}
    public Optional<ApprovalRole> findById(long id){return Optional.ofNullable(mapper.selectById(id)).map(ApprovalRoleEntityMapping::toDomain);}
    public Optional<ApprovalRole> findByKey(String enterpriseId,ApprovalRoleCode code){return Optional.ofNullable(mapper.selectByKey(enterpriseId,code.value())).map(ApprovalRoleEntityMapping::toDomain);}
    public Optional<ApprovalRole> lockByKey(String enterpriseId,ApprovalRoleCode code){return Optional.ofNullable(mapper.selectByKeyForUpdate(enterpriseId,code.value())).map(ApprovalRoleEntityMapping::toDomain);}
    public boolean updateStatus(ApprovalRole role,int expected){return mapper.updateStatus(role.id(),role.status().name(),role.updatedBy(),expected)==1;}
}
