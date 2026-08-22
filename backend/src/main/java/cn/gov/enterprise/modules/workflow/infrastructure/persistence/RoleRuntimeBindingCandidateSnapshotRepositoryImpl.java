package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingCandidateSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingCandidateSnapshot;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingCandidateSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleBindingCandidateSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeBindingCandidateSnapshotRepositoryImpl
        implements RoleRuntimeBindingCandidateSnapshotRepository {
    private final WorkflowRoleBindingCandidateSnapshotMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public RoleRuntimeBindingCandidateSnapshotRepositoryImpl(
            WorkflowRoleBindingCandidateSnapshotMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }

    @Override public void insert(RoleRuntimeBindingCandidateSnapshot value) {
        var entity = RoleRuntimeBindingFoundationEntityMapper.toEntity(value);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B26141", "ROLE candidate snapshot save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B26148", "ROLE candidate snapshot already exists");
        }
    }

    @Override public Optional<RoleRuntimeBindingCandidateSnapshot> findBySnapshotId(String snapshotId) {
        return select(new LambdaQueryWrapper<WorkflowRoleBindingCandidateSnapshotEntity>()
                .eq(WorkflowRoleBindingCandidateSnapshotEntity::getSnapshotId, snapshotId));
    }

    @Override public Optional<RoleRuntimeBindingCandidateSnapshot> findByPromotionId(String promotionId) {
        return select(new LambdaQueryWrapper<WorkflowRoleBindingCandidateSnapshotEntity>()
                .eq(WorkflowRoleBindingCandidateSnapshotEntity::getPromotionId, promotionId));
    }

    @Override public Optional<RoleRuntimeBindingCandidateSnapshot> lockBySnapshotId(String snapshotId) {
        return Optional.ofNullable(mapper.selectForUpdate(snapshotId))
                .map(RoleRuntimeBindingFoundationEntityMapper::toDomain);
    }

    private Optional<RoleRuntimeBindingCandidateSnapshot> select(
            LambdaQueryWrapper<WorkflowRoleBindingCandidateSnapshotEntity> query) {
        return Optional.ofNullable(mapper.selectOne(query))
                .map(RoleRuntimeBindingFoundationEntityMapper::toDomain);
    }
}
