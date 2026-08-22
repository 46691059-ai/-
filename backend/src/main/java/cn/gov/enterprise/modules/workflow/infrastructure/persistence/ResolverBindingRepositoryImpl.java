package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowResolverBindingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ResolverBindingRepositoryImpl implements ResolverBindingRepository {
    private final WorkflowResolverBindingMapper mapper; private final WorkflowPersistenceAudit audit;
    public ResolverBindingRepositoryImpl(WorkflowResolverBindingMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }
    public void saveAll(List<WorkflowResolverBinding> values) {
        for (WorkflowResolverBinding value : values) {
            WorkflowResolverBindingEntity entity = WorkflowResolverBindingEntityMapper.toEntity(value); audit.initialize(entity);
            try { if (mapper.insert(entity) != 1) throw new BusinessException("B2650", "resolver binding save failed"); }
            catch (DuplicateKeyException e) { throw new BusinessException("B2659", "resolver binding already exists"); }
        }
    }
    public List<WorkflowResolverBinding> findByInstanceId(Long instanceId) {
        return mapper.selectList(new LambdaQueryWrapper<WorkflowResolverBindingEntity>()
                .eq(WorkflowResolverBindingEntity::getInstanceId, instanceId)
                .orderByAsc(WorkflowResolverBindingEntity::getResolverCode, WorkflowResolverBindingEntity::getResolverVersion))
                .stream().map(WorkflowResolverBindingEntityMapper::toDomain).toList();
    }
    public Optional<WorkflowResolverBinding> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowResolverBindingEntityMapper::toDomain);
    }
}
