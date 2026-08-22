package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeApprovalRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeApproval;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeBindingApprovalEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeBindingApprovalMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeApprovalRepositoryImpl implements RoleRuntimeApprovalRepository {
    private final RoleRuntimeBindingApprovalMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public RoleRuntimeApprovalRepositoryImpl(
            RoleRuntimeBindingApprovalMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void insert(RoleRuntimeApproval approval) {
        RoleRuntimeBindingApprovalEntity entity = RoleRuntimePersistenceEntityMapper.toEntity(approval);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2690", "ROLE Runtime approval save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2699", "ROLE Runtime approval already exists");
        }
    }

    @Override
    public Optional<RoleRuntimeApproval> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id))
                .map(RoleRuntimePersistenceEntityMapper::toDomain);
    }

    @Override
    public Optional<RoleRuntimeApproval> findByHashes(
            String proposalHash, String eligibilityHash) {
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<RoleRuntimeBindingApprovalEntity>()
                        .eq(RoleRuntimeBindingApprovalEntity::getProposalHash, proposalHash)
                        .eq(RoleRuntimeBindingApprovalEntity::getEligibilityHash, eligibilityHash)))
                .map(RoleRuntimePersistenceEntityMapper::toDomain);
    }
}
