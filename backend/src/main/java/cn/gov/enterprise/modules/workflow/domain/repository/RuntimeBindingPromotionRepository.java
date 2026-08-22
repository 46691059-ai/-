package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import java.util.Optional;

/** Append-only promotion outcome port. */
public interface RuntimeBindingPromotionRepository {
    void insert(RuntimeBindingPromotionDecision decision);
    Optional<RuntimeBindingPromotionDecision> findByPromotionId(String promotionId);
    Optional<RuntimeBindingPromotionDecision> findByPromotionHash(String promotionHash);
}
