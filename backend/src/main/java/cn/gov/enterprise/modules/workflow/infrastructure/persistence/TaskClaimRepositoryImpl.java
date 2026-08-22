package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.repository.TaskClaimRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskClaimEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskClaimMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class TaskClaimRepositoryImpl implements TaskClaimRepository {
    private final WorkflowTaskClaimMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public TaskClaimRepositoryImpl(WorkflowTaskClaimMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }

    @Override
    public Optional<TaskClaim> findByTaskIdAndIdempotencyKey(Long taskId, String key) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkflowTaskClaimEntity>()
                        .eq(WorkflowTaskClaimEntity::getTaskId, taskId)
                        .eq(WorkflowTaskClaimEntity::getIdempotencyKey, key)))
                .map(WorkflowTaskClaimEntityMapper::toDomain);
    }

    @Override
    public Optional<TaskClaim> findActiveByTaskId(Long taskId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkflowTaskClaimEntity>()
                        .eq(WorkflowTaskClaimEntity::getTaskId, taskId)
                        .eq(WorkflowTaskClaimEntity::getActiveToken, 0)))
                .map(WorkflowTaskClaimEntityMapper::toDomain);
    }

    @Override
    public TaskClaim insert(TaskClaim claim) {
        WorkflowTaskClaimEntity entity = WorkflowTaskClaimEntityMapper.toEntity(claim);
        audit.initialize(entity);
        entity.setActiveToken(claim.activeToken());
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B2670", "Claim save failed");
            return claim;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2679", "Claim conflicts with an existing request");
        }
    }
}
