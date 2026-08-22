package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPromotion;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleBindingPromotionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleBindingPromotionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeBindingPromotionRepositoryImpl
        implements RoleRuntimeBindingPromotionRepository {
    private final WorkflowRoleBindingPromotionMapper mapper;
    private final WorkflowPersistenceAudit audit;

    public RoleRuntimeBindingPromotionRepositoryImpl(
            WorkflowRoleBindingPromotionMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }

    @Override public void insert(RoleRuntimeBindingPromotion value) {
        var entity = RoleRuntimeBindingFoundationEntityMapper.toEntity(value);
        audit.initialize(entity);
        try {
            if (mapper.insert(entity) != 1) throw new BusinessException("B26140", "ROLE promotion save failed");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B26149", "ROLE promotion already exists");
        }
    }

    @Override public Optional<RoleRuntimeBindingPromotion> findByPromotionId(String promotionId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkflowRoleBindingPromotionEntity>()
                .eq(WorkflowRoleBindingPromotionEntity::getPromotionId, promotionId)))
                .map(RoleRuntimeBindingFoundationEntityMapper::toDomain);
    }
}
