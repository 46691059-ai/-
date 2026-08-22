package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.repository.RuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Internal framework entry point. Deliberately not a Spring bean or Controller API. */
public final class RuntimeBindingPromotionApplicationService {
    private final RuntimeBindingPromotionPolicy policy;
    private final RuntimeBindingPromotionRepository repository;

    public RuntimeBindingPromotionApplicationService(RuntimeBindingPromotionPolicy policy,
            RuntimeBindingPromotionRepository repository) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public RuntimeBindingPromotionDecision promote(RuntimeBindingPromotionRequest request,
            ActivationAuditTrail activationEvidence, Instant evaluatedAt) {
        if (repository.findByPromotionId(request.promotionId()).isPresent()) {
            throw new IllegalStateException("duplicate runtime binding promotion");
        }
        RuntimeBindingPromotionEvidence evidence = activationEvidence == null
                ? null : RuntimeBindingPromotionEvidence.from(activationEvidence);
        RuntimeBindingPromotionDecision decision = policy.promote(request, evidence, evaluatedAt);
        repository.insert(decision);
        return decision;
    }

    public Optional<RuntimeBindingPromotionDecision> query(String promotionId) {
        return repository.findByPromotionId(promotionId);
    }
}
