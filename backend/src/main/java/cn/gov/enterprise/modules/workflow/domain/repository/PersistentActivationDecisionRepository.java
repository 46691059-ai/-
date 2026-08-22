package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationDecision;
import java.util.List;

public interface PersistentActivationDecisionRepository {
    void insert(PersistentActivationDecision decision);
    List<PersistentActivationDecision> findByActivationId(String activationId);
}
