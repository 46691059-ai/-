package cn.gov.enterprise.modules.workflow.application.command;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityVerificationToken;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle;
import java.util.Objects;

/** Internal Prepare result. It is deliberately not exposed by a Controller. */
public record RoleClaimRuntimeRequest(
        ClaimWorkflowTaskCommand claim,
        RealtimeEligibilityVerificationToken verificationToken,
        RoleRealtimeEligibilityPersistenceBundle evidence) {
    public RoleClaimRuntimeRequest {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(verificationToken, "verificationToken");
        Objects.requireNonNull(evidence, "evidence");
    }
}
