package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;

/** Immutable facts supplied to the readiness gate; it contains no secret values. */
public record RoleDirectoryIntegrationReadinessRequest(
        RoleDirectoryEnvironmentIdentity expectedEnvironment,
        RoleDirectoryEnvironmentIdentity actualEnvironment,
        RoleDirectoryEndpointDescriptor expectedEndpoint,
        RoleDirectoryEndpointDescriptor actualEndpoint,
        RoleDirectoryContractHandshake handshake,
        String expectedCanonicalVectorHash,
        boolean secretProviderAvailable,
        boolean tlsTransportCapable,
        boolean transportConfigurationValid,
        boolean aggregateRevisionMonotonic,
        boolean historicalEffectiveAtSupported,
        boolean completeSemanticsGuaranteed,
        boolean paginationAggregationAtomic,
        boolean candidateLimitGoverned,
        boolean slaCollectionPlanDefined,
        RoleDirectoryCircuitBreaker.State circuitState,
        boolean fakeAdapterPresent,
        int productionAdapterCount,
        RoleDirectoryOwnershipDescriptor ownership,
        RoleDirectoryRaciApproval raciApproval,
        RoleDirectorySecurityReadiness security,
        boolean metricsCapabilityAvailable,
        boolean failureAuditCapabilityAvailable,
        boolean piiWhitelistEnforced,
        boolean productionDependencyPolicyPassed,
        RoleDirectoryCorrelation correlation) { }
