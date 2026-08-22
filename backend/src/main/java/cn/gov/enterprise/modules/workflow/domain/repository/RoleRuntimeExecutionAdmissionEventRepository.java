package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionEvent;
import java.util.List;

public interface RoleRuntimeExecutionAdmissionEventRepository {
    void append(RoleRuntimeExecutionAdmissionEvent event);
    List<RoleRuntimeExecutionAdmissionEvent> findByAdmissionId(String admissionId);
}
