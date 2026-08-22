package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingCandidateSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingLifecycleRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeBindingPromotionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPersistenceBundle;
import cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingPersistencePolicy;
import cn.gov.enterprise.modules.workflow.domain.role.promotion.RuntimeBindingPromotionDecision;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal persistence boundary. It cannot create or enable any Workflow runtime object. */
@Service
public class RoleRuntimeBindingPersistenceApplicationService {
    private final RoleRuntimeBindingPromotionRepository promotions;
    private final RoleRuntimeBindingCandidateSnapshotRepository snapshots;
    private final RoleRuntimeBindingLifecycleRepository lifecycle;
    private final WorkflowIdentityGenerator ids;
    private final RoleRuntimeBindingPersistencePolicy policy =
            new RoleRuntimeBindingPersistencePolicy();

    public RoleRuntimeBindingPersistenceApplicationService(
            RoleRuntimeBindingPromotionRepository promotions,
            RoleRuntimeBindingCandidateSnapshotRepository snapshots,
            RoleRuntimeBindingLifecycleRepository lifecycle,
            WorkflowIdentityGenerator ids) {
        this.promotions = promotions;
        this.snapshots = snapshots;
        this.lifecycle = lifecycle;
        this.ids = ids;
    }

    @Transactional
    public RoleRuntimeBindingPersistenceBundle persist(
            String snapshotId, ActivationAuditTrail activationEvidence,
            RuntimeBindingPromotionDecision promotionDecision,
            String operatorId, Instant persistedAt) {
        String promotionId = promotionDecision.request().promotionId();
        if (promotions.findByPromotionId(promotionId).isPresent()) {
            throw new BusinessException("B26149", "ROLE promotion already persisted");
        }
        if (snapshots.findBySnapshotId(snapshotId).isPresent()
                || snapshots.findByPromotionId(promotionId).isPresent()) {
            throw new BusinessException("B26148", "ROLE candidate snapshot already persisted");
        }
        RoleRuntimeBindingPersistenceBundle bundle = policy.freeze(snapshotId,
                activationEvidence, promotionDecision, operatorId, persistedAt,
                () -> ids.nextId());
        promotions.insert(bundle.promotion());
        snapshots.insert(bundle.snapshot());
        bundle.lifecycleEvents().forEach(lifecycle::append);
        return bundle;
    }
}
