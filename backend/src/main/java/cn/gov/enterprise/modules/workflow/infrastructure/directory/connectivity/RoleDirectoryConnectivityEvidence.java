package cn.gov.enterprise.modules.workflow.infrastructure.directory.connectivity;

import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEnvironmentIdentity;
import java.time.Instant;

/** Immutable, secret-free connectivity evidence. Certificate subject is represented by a hash only. */
public record RoleDirectoryConnectivityEvidence(
        String providerCode, String providerVersion, RoleDirectoryEnvironmentIdentity environmentIdentity,
        String endpointIdentityHash, String contractVersion, String contractHash,
        String canonicalVersion, String tlsStatus, String authenticationStatus,
        boolean revisionCapability, boolean historicalCapability, boolean completeCapability,
        Instant checkedAt, String outcome, String evidenceHash) { }
