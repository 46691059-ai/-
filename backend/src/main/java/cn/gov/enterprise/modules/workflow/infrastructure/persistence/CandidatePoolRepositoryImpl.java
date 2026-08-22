package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowCandidatePoolMemberEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowCandidatePoolMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowCandidatePoolMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class CandidatePoolRepositoryImpl implements CandidatePoolRepository {
    private final WorkflowCandidatePoolMapper poolMapper;
    private final WorkflowCandidatePoolMemberMapper memberMapper;
    private final WorkflowPersistenceAudit audit;

    public CandidatePoolRepositoryImpl(
            WorkflowCandidatePoolMapper poolMapper,
            WorkflowCandidatePoolMemberMapper memberMapper,
            WorkflowPersistenceAudit audit) {
        this.poolMapper = poolMapper;
        this.memberMapper = memberMapper;
        this.audit = audit;
    }

    @Override
    public void save(CandidatePool pool) {
        WorkflowCandidatePoolEntity entity = WorkflowCandidatePoolEntityMapper.toEntity(pool);
        audit.initialize(entity);
        try {
            if (poolMapper.insert(entity) != 1) {
                throw new BusinessException("B2660", "workflow Candidate Pool save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2669", "workflow Candidate Pool already exists");
        }
    }

    @Override
    public Optional<CandidatePool> findByTaskId(Long taskId) {
        WorkflowCandidatePoolEntity pool = poolMapper.selectOne(
                new LambdaQueryWrapper<WorkflowCandidatePoolEntity>()
                        .eq(WorkflowCandidatePoolEntity::getTaskId, taskId));
        if (pool == null) return Optional.empty();
        List<CandidatePoolMember> members = memberMapper.selectList(
                        new LambdaQueryWrapper<WorkflowCandidatePoolMemberEntity>()
                                .eq(WorkflowCandidatePoolMemberEntity::getPoolId, pool.getId())
                                .orderByAsc(WorkflowCandidatePoolMemberEntity::getSortOrder,
                                        WorkflowCandidatePoolMemberEntity::getCandidateUserId))
                .stream().map(WorkflowCandidatePoolEntityMapper::toDomain).toList();
        return Optional.of(WorkflowCandidatePoolEntityMapper.toDomain(pool, members));
    }

    @Override
    public boolean existsByTaskId(Long taskId) {
        return poolMapper.selectCount(new LambdaQueryWrapper<WorkflowCandidatePoolEntity>()
                .eq(WorkflowCandidatePoolEntity::getTaskId, taskId)) > 0;
    }

    @Override
    public Optional<CandidatePool> findByTaskIdForUpdate(Long taskId) {
        WorkflowCandidatePoolEntity pool = poolMapper.selectByTaskIdForUpdate(taskId);
        if (pool == null) return Optional.empty();
        List<CandidatePoolMember> members = memberMapper.selectList(
                        new LambdaQueryWrapper<WorkflowCandidatePoolMemberEntity>()
                                .eq(WorkflowCandidatePoolMemberEntity::getPoolId, pool.getId())
                                .orderByAsc(WorkflowCandidatePoolMemberEntity::getSortOrder,
                                        WorkflowCandidatePoolMemberEntity::getCandidateUserId))
                .stream().map(WorkflowCandidatePoolEntityMapper::toDomain).toList();
        return Optional.of(WorkflowCandidatePoolEntityMapper.toDomain(pool, members));
    }

    @Override
    public boolean claim(CandidatePool pool, int expectedVersion) {
        return poolMapper.claim(pool.id(), expectedVersion, audit.operator()) == 1;
    }
}
