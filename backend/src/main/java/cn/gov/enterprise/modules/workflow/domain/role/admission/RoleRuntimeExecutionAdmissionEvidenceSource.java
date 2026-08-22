package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.util.Optional;

/** Read-only source of already frozen governance evidence. */
public interface RoleRuntimeExecutionAdmissionEvidenceSource {
    Optional<RoleRuntimeExecutionAdmissionFacts> load(String runtimeBindingCandidateId);
}
