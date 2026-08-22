package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationDecisionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationApprovalEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeActivationApprovalMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class PersistentActivationDecisionRepositoryImpl implements PersistentActivationDecisionRepository {
    private final RoleRuntimeActivationApprovalMapper mapper; private final WorkflowPersistenceAudit audit;
    public PersistentActivationDecisionRepositoryImpl(RoleRuntimeActivationApprovalMapper mapper, WorkflowPersistenceAudit audit) { this.mapper = mapper; this.audit = audit; }
    @Override public void insert(PersistentActivationDecision value) {
        RoleRuntimeActivationApprovalEntity entity = ActivationPersistenceEntityMapper.toEntity(value); audit.initialize(entity);
        try { if (mapper.insert(entity) != 1) throw new BusinessException("B26130", "activation decision save failed"); }
        catch (DuplicateKeyException exception) { throw new BusinessException("B26139", "activation decision already exists"); }
    }
    @Override public List<PersistentActivationDecision> findByActivationId(String activationId) {
        return mapper.selectList(new LambdaQueryWrapper<RoleRuntimeActivationApprovalEntity>()
                .eq(RoleRuntimeActivationApprovalEntity::getActivationId, activationId)
                .orderByAsc(RoleRuntimeActivationApprovalEntity::getDecisionTime)).stream()
                .map(ActivationPersistenceEntityMapper::toDomain).toList();
    }
}
