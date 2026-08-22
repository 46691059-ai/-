package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import java.time.Instant;
import java.util.List;

/** PII-free readiness evidence. It records identities and hashes, never credentials or members. */
public record RoleDirectoryIntegrationEvidence(
        Instant evaluatedAt, RoleDirectoryEnvironmentIdentity environmentIdentity,
        String providerCode, String serviceIdentity, String contractVersion,
        String contractHash, String canonicalVersion, List<String> passedChecks,
        RoleDirectoryCorrelation correlation) {
    public RoleDirectoryIntegrationEvidence { passedChecks = List.copyOf(passedChecks); }
}
