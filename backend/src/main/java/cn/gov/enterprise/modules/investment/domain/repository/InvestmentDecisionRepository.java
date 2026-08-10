package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.DecisionSnapshot;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.model.WorkflowBinding;
import java.util.Optional;

public interface InvestmentDecisionRepository {
    void save(InvestmentDecisionCase decision);
    Optional<InvestmentDecisionCase> findById(Long id);
    Optional<InvestmentDecisionCase> findByIdForUpdate(Long id);
    boolean existsByDecisionNo(String decisionNo);
    int nextSnapshotVersion(Long decisionId);
    int nextAttemptNo(Long decisionId);
    DecisionMaterials requireFrozenMaterials(Long investmentId);
    void saveSnapshot(DecisionSnapshot snapshot);
    void saveBinding(WorkflowBinding binding, Long enterpriseId, String snapshotHash,
                     String definitionKey, int definitionVersion, String requestHash, String traceId);
    Optional<WorkflowBinding> findCurrentBinding(Long decisionId);
    void updateDecisionState(Long decisionId, InvestmentDecisionCase.Status status, Long snapshotId);

    record DecisionMaterials(
            Long schemeVersionId, String schemeStatus, String schemeHash,
            Long feasibilityVersionId, String feasibilityStatus, String feasibilityConclusion, String feasibilityHash,
            Long dueDiligencePackageId, String dueDiligenceStatus, String dueDiligenceConclusion,
            int openBlockingCount, String dueDiligenceHash, Long enterpriseId) {}
}
