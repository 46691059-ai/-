package cn.gov.enterprise.modules.workflow.infrastructure.directory.connectivity;

import java.util.List;

public record RoleDirectoryConnectivityResult(
        Status status, List<RoleDirectoryConnectivityBlockReason> blockReasons,
        RoleDirectoryConnectivityEvidence evidence, boolean resolverExecutionEligible) {
    public enum Status { READY_FOR_INTEGRATION_TEST, BLOCKED }
    public RoleDirectoryConnectivityResult { blockReasons = List.copyOf(blockReasons); }
}
