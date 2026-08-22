package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingLifecycleRepository;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingLifecycleEvent;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingSnapshotEventEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleBindingSnapshotEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeBindingLifecycleRepositoryImpl
        implements RoleRuntimeBindingLifecycleRepository {
    private final WorkflowRoleBindingSnapshotEventMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public RoleRuntimeBindingLifecycleRepositoryImpl(
            WorkflowRoleBindingSnapshotEventMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }

    @Override public void append(RoleRuntimeBindingLifecycleEvent value) {
        var entity = RoleRuntimeBindingFoundationEntityMapper.toEntity(value);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B26142", "ROLE lifecycle append failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B26147", "ROLE lifecycle event already exists");
        }
    }

    @Override public Optional<RoleRuntimeBindingLifecycleEvent> findLatest(String snapshotId) {
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<WorkflowRoleBindingSnapshotEventEntity>()
                        .eq(WorkflowRoleBindingSnapshotEventEntity::getSnapshotId, snapshotId)
                        .orderByDesc(WorkflowRoleBindingSnapshotEventEntity::getSequenceNo)
                        .last("LIMIT 1")))
                .map(RoleRuntimeBindingFoundationEntityMapper::toDomain);
    }

    @Override public List<RoleRuntimeBindingLifecycleEvent> list(String snapshotId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowRoleBindingSnapshotEventEntity>()
                        .eq(WorkflowRoleBindingSnapshotEventEntity::getSnapshotId, snapshotId)
                        .orderByAsc(WorkflowRoleBindingSnapshotEventEntity::getSequenceNo))
                .stream().map(RoleRuntimeBindingFoundationEntityMapper::toDomain).toList();
    }
}
