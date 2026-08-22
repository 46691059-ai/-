package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeBindingSnapshot;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeBindingSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeBindingSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeBindingSnapshotRepositoryImpl
        implements RoleRuntimeBindingSnapshotRepository {
    private final WorkflowRoleRuntimeBindingSnapshotMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public RoleRuntimeBindingSnapshotRepositoryImpl(
            WorkflowRoleRuntimeBindingSnapshotMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void insert(RoleRuntimeBindingSnapshot snapshot) {
        WorkflowRoleRuntimeBindingSnapshotEntity entity =
                RoleRuntimePersistenceEntityMapper.toEntity(snapshot);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) {
                throw new BusinessException("B2690", "ROLE Runtime Binding Snapshot save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2699", "ROLE Runtime Binding Snapshot already exists");
        }
    }

    @Override
    public Optional<RoleRuntimeBindingSnapshot> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id))
                .map(RoleRuntimePersistenceEntityMapper::toDomain);
    }

    @Override
    public Optional<RoleRuntimeBindingSnapshot> findByInstanceIdAndNodeId(
            Long instanceId, Long nodeId) {
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<WorkflowRoleRuntimeBindingSnapshotEntity>()
                        .eq(WorkflowRoleRuntimeBindingSnapshotEntity::getInstanceId, instanceId)
                        .eq(WorkflowRoleRuntimeBindingSnapshotEntity::getNodeId, nodeId)))
                .map(RoleRuntimePersistenceEntityMapper::toDomain);
    }
}
