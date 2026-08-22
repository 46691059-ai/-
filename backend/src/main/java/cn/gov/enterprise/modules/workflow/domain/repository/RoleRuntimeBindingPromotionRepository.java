package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPromotion;
import java.util.Optional;

public interface RoleRuntimeBindingPromotionRepository {
    void insert(RoleRuntimeBindingPromotion promotion);
    Optional<RoleRuntimeBindingPromotion> findByPromotionId(String promotionId);
}
