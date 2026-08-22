package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import java.util.Optional;

public interface PersistentActivationRequestRepository {
    void insert(PersistentActivationRequest request);
    Optional<PersistentActivationRequest> findByActivationId(String activationId);
}
