package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.ActivationEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationDecisionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationRequestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationAuditTrail;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationPersistencePolicy;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only persistence entry point. It deliberately exposes no enable operation. */
@Service
public class RoleRuntimeActivationPersistenceService {
    private final PersistentActivationRequestRepository requests;
    private final PersistentActivationDecisionRepository decisions;
    private final ActivationEvidenceRepository evidence;
    private final WorkflowIdentityGenerator ids;
    private final ActivationPersistencePolicy policy = new ActivationPersistencePolicy();

    public RoleRuntimeActivationPersistenceService(PersistentActivationRequestRepository requests,
            PersistentActivationDecisionRepository decisions, ActivationEvidenceRepository evidence,
            WorkflowIdentityGenerator ids) {
        this.requests = requests; this.decisions = decisions; this.evidence = evidence; this.ids = ids;
    }

    @Transactional
    public ActivationAuditTrail persistApproved(String activationId,
            RoleRuntimeActivationSnapshot snapshot, long directoryRevision) {
        if (requests.findByActivationId(activationId).isPresent()) {
            throw new BusinessException("B26139", "activation evidence already exists");
        }
        ActivationAuditTrail trail = policy.freezeApproved(activationId, snapshot, directoryRevision, ids::nextId);
        requests.insert(trail.request());
        trail.decisions().forEach(decisions::insert);
        trail.evidence().forEach(evidence::insert);
        return trail;
    }
}
