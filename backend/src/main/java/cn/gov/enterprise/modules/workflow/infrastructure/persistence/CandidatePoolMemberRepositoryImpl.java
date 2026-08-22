package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolMemberEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowCandidatePoolMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class CandidatePoolMemberRepositoryImpl implements CandidatePoolMemberRepository {
    private final WorkflowCandidatePoolMemberMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public CandidatePoolMemberRepositoryImpl(
            WorkflowCandidatePoolMemberMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void saveAll(List<CandidatePoolMember> members) {
        try {
            for (CandidatePoolMember member : List.copyOf(members)) {
                WorkflowCandidatePoolMemberEntity entity =
                        WorkflowCandidatePoolEntityMapper.toEntity(member);
                audit.initialize(entity);
                if (mapper.insert(entity) != 1) {
                    throw new BusinessException("B2660", "workflow Candidate Member save failed");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2669", "workflow Candidate Member already exists");
        }
    }

    @Override
    public List<CandidatePoolMember> findByPoolId(Long poolId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowCandidatePoolMemberEntity>()
                        .eq(WorkflowCandidatePoolMemberEntity::getPoolId, poolId)
                        .orderByAsc(WorkflowCandidatePoolMemberEntity::getSortOrder,
                                WorkflowCandidatePoolMemberEntity::getCandidateUserId))
                .stream().map(WorkflowCandidatePoolEntityMapper::toDomain).toList();
    }

    @Override
    public Optional<CandidatePoolMember> findByPoolIdAndUserIdForUpdate(Long poolId, Long userId) {
        return Optional.ofNullable(mapper.selectByPoolAndUserForUpdate(poolId, userId))
                .map(WorkflowCandidatePoolEntityMapper::toDomain);
    }
}
