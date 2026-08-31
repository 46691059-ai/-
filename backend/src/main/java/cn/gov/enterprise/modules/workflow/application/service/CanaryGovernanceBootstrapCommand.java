package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryApprovalEvidence;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import java.util.Objects;

/** Audited, exact-scope input for creating a pre-enable Canary governance ledger. */
public record CanaryGovernanceBootstrapCommand(CanaryScope scope,
        CanaryApprovalEvidence evidence, String preparationCommit,
        String authorizationCommit, String preEnableBaselineCommit,
        String humanApprovalRecordCommit, String postTagAttestationCommit,
        String runtimeTagObject, String approvalActor) {
    public CanaryGovernanceBootstrapCommand {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(evidence);
        sha(preparationCommit);
        sha(authorizationCommit);
        sha(preEnableBaselineCommit);
        sha(humanApprovalRecordCommit);
        sha(postTagAttestationCommit);
        sha(runtimeTagObject);
        if (approvalActor == null || approvalActor.isBlank() || approvalActor.length() > 100) {
            throw new IllegalArgumentException("approvalActor is required");
        }
    }

    public String bindingReason() {
        return "RC2_RUNTIME_BOOTSTRAP_V1|prep=" + preparationCommit
                + "|auth=" + authorizationCommit + "|baseline=" + preEnableBaselineCommit
                + "|approval=" + humanApprovalRecordCommit + "|attestation="
                + postTagAttestationCommit + "|tagObject=" + runtimeTagObject;
    }

    private static void sha(String value) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("bootstrap identity must be lowercase Git SHA-1");
        }
    }
}
