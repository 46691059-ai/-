package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.repository.RuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Non-production append-only adapter used until a separately approved Migration exists. */
public final class InMemoryRuntimeBindingPromotionRepository
        implements RuntimeBindingPromotionRepository {
    private final Map<String, RuntimeBindingPromotionDecision> decisions = new ConcurrentHashMap<>();

    @Override
    public void insert(RuntimeBindingPromotionDecision decision) {
        if (decision == null) throw new IllegalArgumentException("decision must not be null");
        if (decisions.putIfAbsent(decision.request().promotionId(), decision) != null) {
            throw new IllegalStateException("duplicate runtime binding promotion");
        }
    }

    @Override
    public Optional<RuntimeBindingPromotionDecision> findByPromotionId(String promotionId) {
        return Optional.ofNullable(decisions.get(promotionId));
    }

    @Override
    public Optional<RuntimeBindingPromotionDecision> findByPromotionHash(String promotionHash) {
        return decisions.values().stream()
                .filter(item -> item.candidateOptional()
                        .map(candidate -> candidate.promotionHash().equals(promotionHash)).orElse(false))
                .findFirst();
    }
}
