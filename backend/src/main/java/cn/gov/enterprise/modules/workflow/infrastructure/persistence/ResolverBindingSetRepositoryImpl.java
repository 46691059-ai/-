package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.WorkflowResolverBindingSet;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingSetRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowResolverBindingSetEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowResolverBindingSetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ResolverBindingSetRepositoryImpl implements ResolverBindingSetRepository {
    private final WorkflowResolverBindingSetMapper mapper; private final WorkflowPersistenceAudit audit;
    public ResolverBindingSetRepositoryImpl(WorkflowResolverBindingSetMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }
    public void save(WorkflowResolverBindingSet value) {
        WorkflowResolverBindingSetEntity entity = WorkflowResolverBindingEntityMapper.toEntity(value); audit.initialize(entity);
        try { if (mapper.insert(entity) != 1) throw new BusinessException("B2650", "resolver binding set save failed"); }
        catch (DuplicateKeyException e) { throw new BusinessException("B2659", "resolver binding set already exists"); }
    }
    public Optional<WorkflowResolverBindingSet> findByInstanceId(Long instanceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkflowResolverBindingSetEntity>()
                .eq(WorkflowResolverBindingSetEntity::getInstanceId, instanceId)))
                .map(WorkflowResolverBindingEntityMapper::toDomain);
    }
}
