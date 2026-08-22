package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionEvidenceRecord;
import java.util.List;

public interface RoleRuntimeExecutionAdmissionEvidenceRepository {
    void appendAll(List<RoleRuntimeExecutionAdmissionEvidenceRecord> evidence);
    List<RoleRuntimeExecutionAdmissionEvidenceRecord> findByAdmissionId(String admissionId);
}
