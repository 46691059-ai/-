package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.NodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.repository.NodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeResolverBindingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class NodeResolverBindingRepositoryImpl implements NodeResolverBindingRepository {
    private final WorkflowNodeResolverBindingMapper mapper; private final WorkflowPersistenceAudit audit;
    public NodeResolverBindingRepositoryImpl(WorkflowNodeResolverBindingMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }
    public void saveAll(List<NodeResolverBinding> values) {
        for (NodeResolverBinding value : values) {
            WorkflowNodeResolverBindingEntity entity = WorkflowResolverBindingEntityMapper.toEntity(value); audit.initialize(entity);
            try { if (mapper.insert(entity) != 1) throw new BusinessException("B2650", "node resolver binding save failed"); }
            catch (DuplicateKeyException e) { throw new BusinessException("B2659", "node resolver binding already exists"); }
        }
    }
    public List<NodeResolverBinding> findByInstanceId(Long instanceId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowNodeResolverBindingEntity>()
                .eq(WorkflowNodeResolverBindingEntity::getInstanceId, instanceId)
                .orderByAsc(WorkflowNodeResolverBindingEntity::getNodeCodeSnapshot))
                .stream().map(WorkflowResolverBindingEntityMapper::toDomain).toList();
    }
    public Optional<NodeResolverBinding> findByInstanceIdAndNodeId(Long instanceId, Long nodeId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkflowNodeResolverBindingEntity>()
                .eq(WorkflowNodeResolverBindingEntity::getInstanceId, instanceId)
                .eq(WorkflowNodeResolverBindingEntity::getNodeId, nodeId)))
                .map(WorkflowResolverBindingEntityMapper::toDomain);
    }
}
