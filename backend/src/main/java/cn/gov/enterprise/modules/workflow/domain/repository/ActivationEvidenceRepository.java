package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord;
import java.util.List;

public interface ActivationEvidenceRepository {
    void insert(ActivationEvidenceRecord evidence);
    List<ActivationEvidenceRecord> findByActivationId(String activationId);
}
