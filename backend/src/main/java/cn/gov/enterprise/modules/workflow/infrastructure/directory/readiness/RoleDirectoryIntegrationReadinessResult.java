package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import java.util.List;

public record RoleDirectoryIntegrationReadinessResult(
        Status status, List<RoleDirectoryIntegrationBlockReason> blockReasons,
        RoleDirectoryIntegrationEvidence evidence, boolean resolverExecutionEligible) {
    public enum Status { NOT_READY, READY_FOR_CONNECTIVITY_TEST, READY_FOR_INTEGRATION_TEST, BLOCKED }
    public RoleDirectoryIntegrationReadinessResult { blockReasons = List.copyOf(blockReasons); }
}
