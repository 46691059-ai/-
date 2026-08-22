package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowNodeRepositoryImpl implements WorkflowNodeRepository {
    private final WorkflowNodeMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public WorkflowNodeRepositoryImpl(WorkflowNodeMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override public Optional<WorkflowNode> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntityMapper::toDomain);
    }

    @Override public List<WorkflowNode> findByVersionId(Long versionId) {
        return findByVersionIds(List.of(versionId));
    }

    @Override public List<WorkflowNode> findByVersionIds(List<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<WorkflowNodeEntity>()
                        .in(WorkflowNodeEntity::getVersionId, versionIds)
                        .orderByAsc(WorkflowNodeEntity::getVersionId, WorkflowNodeEntity::getNodeOrder))
                .stream().map(WorkflowEntityMapper::toDomain).toList();
    }

    @Override public void replaceNodes(Long versionId, List<WorkflowNode> nodes) {
        String operator = audit.operator();
        mapper.logicalDeleteActiveByVersionId(versionId, operator);
        try {
            for (WorkflowNode node : nodes) {
                WorkflowNodeEntity entity = WorkflowEntityMapper.toEntity(node);
                audit.initialize(entity);
                if (mapper.insert(entity) != 1) throw new BusinessException("B2500", "workflow node save failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B2509", "workflow node code or order is duplicated");
        }
    }
}
