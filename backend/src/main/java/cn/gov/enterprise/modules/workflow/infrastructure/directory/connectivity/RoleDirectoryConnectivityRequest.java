package cn.gov.enterprise.modules.workflow.infrastructure.directory.connectivity;

import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCapabilityDescriptor;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryContractHandshake;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEndpointDescriptor;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEnvironmentIdentity;
import java.time.Instant;

/** Collected TEST/PREPROD probe facts. It cannot carry credential or member payloads. */
public record RoleDirectoryConnectivityRequest(
        RoleDirectoryEnvironmentIdentity expectedEnvironment,
        RoleDirectoryEndpointDescriptor expectedEndpoint,
        RoleDirectoryEndpointDescriptor actualEndpoint,
        RoleDirectoryContractHandshake handshake,
        String expectedCanonicalVectorHash,
        SecretSourceType secretSourceType,
        String credentialIdentityHash,
        boolean tlsEnabled,
        boolean certificateValid,
        boolean hostnameVerificationEnabled,
        String tlsVersion,
        String certificateSubjectHash,
        Instant certificateExpiry,
        AuthenticationStatus authenticationStatus,
        boolean revisionCapabilityValid,
        boolean historicalCapabilityValid,
        boolean completeSemanticsValid,
        boolean paginationSemanticsValid,
        boolean timeoutPolicyValid,
        boolean retryPolicyValid,
        int attempts,
        RoleDirectoryCircuitBreaker.State circuitState,
        RoleDirectoryCapabilityDescriptor.AdapterType selectedAdapterType,
        int productionAdapterCount,
        boolean startupGatePassed,
        long productionEndpointCalls,
        long productionSecretReads,
        long roleMemberQueries,
        long candidateResolutions,
        long candidatePoolWrites,
        long taskCreates,
        long claimExecutions) {
    public enum SecretSourceType { TEST_SECRET_MANAGER, SECURE_ENVIRONMENT, UNIFIED_INJECTION, MISSING }
    public enum AuthenticationStatus { PASS, FAIL, NOT_EXECUTED }
}
