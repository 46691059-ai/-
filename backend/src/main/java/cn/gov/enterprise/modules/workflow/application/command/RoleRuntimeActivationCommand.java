package cn.gov.enterprise.modules.workflow.application.command;

import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import java.util.Objects;

public record RoleRuntimeActivationCommand(CanaryScope scope, String eventId,
        String authorizationId, String authorizationType, String authorizationCommit,
        String designCommit, String observationEvidenceCommit,
        String runtimeEnablementEvidenceCommit, String runtimeReleaseCommit,
        String runtimeReleaseTag, String approvalEvidenceHash, String directoryResultHash,
        String versionBindingHash, String manifestHash, String contentHash,
        String structuralFingerprint, String actorType, String actorId) {
    public RoleRuntimeActivationCommand { Objects.requireNonNull(scope, "scope"); }
}
