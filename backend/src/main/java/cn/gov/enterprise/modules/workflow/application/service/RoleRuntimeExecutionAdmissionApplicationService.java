package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionDecision;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionEvidenceSource;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionRequest;
import java.time.Instant;
import java.util.Objects;

/** Internal, in-memory governance entry point. It is intentionally not a Spring bean or API. */
public final class RoleRuntimeExecutionAdmissionApplicationService {
    private final RoleRuntimeExecutionAdmissionEvidenceSource evidenceSource;
    private final RoleRuntimeExecutionAdmissionPolicy policy;

    public RoleRuntimeExecutionAdmissionApplicationService(
            RoleRuntimeExecutionAdmissionEvidenceSource evidenceSource,
            RoleRuntimeExecutionAdmissionPolicy policy) {
        this.evidenceSource = Objects.requireNonNull(evidenceSource, "evidenceSource");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public RoleRuntimeExecutionAdmissionDecision admit(
            RoleRuntimeExecutionAdmissionRequest request, Instant validatedAt) {
        Objects.requireNonNull(request, "request");
        return policy.evaluate(request,
                evidenceSource.load(request.runtimeBindingCandidateId()).orElse(null),
                validatedAt);
    }
}
