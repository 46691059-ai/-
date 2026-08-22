package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fail-closed pre-connectivity gate. It performs no network call and cannot mutate Registry. */
public final class RoleDirectoryIntegrationReadiness {
    public static final String CANONICAL_VERSION = "ROLE_CANONICAL_JSON_V1";
    private final Clock clock;

    public RoleDirectoryIntegrationReadiness() { this(Clock.systemUTC()); }
    RoleDirectoryIntegrationReadiness(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public RoleDirectoryIntegrationReadinessResult evaluate(RoleDirectoryIntegrationReadinessRequest request) {
        Objects.requireNonNull(request, "request");
        List<RoleDirectoryIntegrationBlockReason> reasons = new ArrayList<>();
        List<String> passed = new ArrayList<>();

        check(request.expectedEnvironment() != null
                        && request.expectedEnvironment() == request.actualEnvironment()
                        && request.handshake() != null
                        && request.expectedEnvironment() == request.handshake().environmentIdentity(),
                RoleDirectoryIntegrationBlockReason.ENVIRONMENT_MISMATCH, "environment", reasons, passed);
        check(request.expectedEndpoint() != null && request.expectedEndpoint().equals(request.actualEndpoint()),
                RoleDirectoryIntegrationBlockReason.ENDPOINT_IDENTITY_MISMATCH, "endpoint", reasons, passed);
        check(providerMatches(request), RoleDirectoryIntegrationBlockReason.PROVIDER_MISMATCH,
                "provider", reasons, passed);
        check(request.secretProviderAvailable(), RoleDirectoryIntegrationBlockReason.SECRET_PROVIDER_MISSING,
                "secret-provider-capability", reasons, passed);
        check(tlsReady(request), RoleDirectoryIntegrationBlockReason.TLS_CAPABILITY_MISSING,
                "tls", reasons, passed);
        check(request.transportConfigurationValid(),
                RoleDirectoryIntegrationBlockReason.TRANSPORT_CONFIGURATION_INVALID,
                "typed-transport-config", reasons, passed);
        check(contractMatches(request), RoleDirectoryIntegrationBlockReason.CONTRACT_MISMATCH,
                "contract", reasons, passed);
        check(canonicalMatches(request), RoleDirectoryIntegrationBlockReason.CANONICAL_MISMATCH,
                "canonical-vector", reasons, passed);
        check(request.aggregateRevisionMonotonic() && supports(request, "AGGREGATE_REVISION"),
                RoleDirectoryIntegrationBlockReason.REVISION_CAPABILITY_UNSUPPORTED,
                "aggregate-revision", reasons, passed);
        check(request.historicalEffectiveAtSupported() && supports(request, "HISTORICAL_EFFECTIVE_AT"),
                RoleDirectoryIntegrationBlockReason.HISTORICAL_QUERY_UNSUPPORTED,
                "historical-effective-at", reasons, passed);
        check(request.completeSemanticsGuaranteed() && supports(request, "COMPLETE_RESULT"),
                RoleDirectoryIntegrationBlockReason.COMPLETE_SEMANTICS_UNSUPPORTED,
                "complete-semantics", reasons, passed);
        check(request.paginationAggregationAtomic(), RoleDirectoryIntegrationBlockReason.PAGINATION_INCOMPLETE,
                "pagination-atomic", reasons, passed);
        check(request.candidateLimitGoverned(), RoleDirectoryIntegrationBlockReason.CANDIDATE_LIMIT_UNGOVERNED,
                "candidate-limit", reasons, passed);
        check(request.slaCollectionPlanDefined(), RoleDirectoryIntegrationBlockReason.SLA_PLAN_MISSING,
                "sla-plan", reasons, passed);
        check(request.circuitState() == RoleDirectoryCircuitBreaker.State.CLOSED,
                RoleDirectoryIntegrationBlockReason.CIRCUIT_NOT_CLOSED, "circuit-closed", reasons, passed);
        check(fakeAllowed(request), RoleDirectoryIntegrationBlockReason.FAKE_ADAPTER_FORBIDDEN,
                "fake-isolation", reasons, passed);
        check(request.productionAdapterCount() == 1,
                RoleDirectoryIntegrationBlockReason.PRODUCTION_ADAPTER_NOT_UNIQUE,
                "single-production-adapter", reasons, passed);
        check(request.ownership() != null && request.ownership().complete(),
                RoleDirectoryIntegrationBlockReason.OWNERSHIP_MISSING, "ownership", reasons, passed);
        check(request.raciApproval() != null && request.raciApproval().complete(),
                RoleDirectoryIntegrationBlockReason.RACI_APPROVAL_MISSING, "raci", reasons, passed);
        check(request.security() != null && request.security().complete(),
                RoleDirectoryIntegrationBlockReason.SECURITY_REQUIREMENT_MISSING, "security", reasons, passed);
        check(request.metricsCapabilityAvailable(), RoleDirectoryIntegrationBlockReason.METRICS_CAPABILITY_MISSING,
                "metrics", reasons, passed);
        check(request.failureAuditCapabilityAvailable(), RoleDirectoryIntegrationBlockReason.AUDIT_CAPABILITY_MISSING,
                "failure-audit", reasons, passed);
        check(request.piiWhitelistEnforced(), RoleDirectoryIntegrationBlockReason.PII_POLICY_MISSING,
                "pii-whitelist", reasons, passed);
        check(request.productionDependencyPolicyPassed(),
                RoleDirectoryIntegrationBlockReason.PRODUCTION_DEPENDENCY_VIOLATION,
                "dependency-policy", reasons, passed);

        RoleDirectoryIntegrationEvidence evidence = evidence(request, passed);
        var status = reasons.isEmpty()
                ? RoleDirectoryIntegrationReadinessResult.Status.READY_FOR_CONNECTIVITY_TEST
                : RoleDirectoryIntegrationReadinessResult.Status.BLOCKED;
        return new RoleDirectoryIntegrationReadinessResult(status, reasons, evidence, false);
    }

    private boolean providerMatches(RoleDirectoryIntegrationReadinessRequest request) {
        return request.expectedEndpoint() != null && request.actualEndpoint() != null
                && request.handshake() != null
                && request.expectedEndpoint().providerCode().equals(request.handshake().providerCode())
                && request.actualEndpoint().providerCode().equals(request.handshake().providerCode());
    }

    private boolean tlsReady(RoleDirectoryIntegrationReadinessRequest request) {
        if (!request.tlsTransportCapable() || request.actualEndpoint() == null) return false;
        if (request.expectedEnvironment() == RoleDirectoryEnvironmentIdentity.LOCAL
                || request.expectedEnvironment() == RoleDirectoryEnvironmentIdentity.TEST) return true;
        return "https".equalsIgnoreCase(request.actualEndpoint().endpointIdentity().getScheme());
    }

    private boolean contractMatches(RoleDirectoryIntegrationReadinessRequest request) {
        if (request.handshake() == null || request.expectedEndpoint() == null) return false;
        return RoleDirectoryResolver.PORT_CONTRACT.equals(request.handshake().contractVersion())
                && RoleDirectoryResolver.CONTRACT_HASH.value().equals(request.handshake().contractHash())
                && request.expectedEndpoint().contractVersion().equals(request.handshake().contractVersion())
                && request.expectedEndpoint().contractHash().equals(request.handshake().contractHash());
    }

    private boolean canonicalMatches(RoleDirectoryIntegrationReadinessRequest request) {
        return request.handshake() != null && CANONICAL_VERSION.equals(request.handshake().canonicalVersion())
                && request.expectedCanonicalVectorHash() != null
                && request.expectedCanonicalVectorHash().matches("[0-9a-f]{64}")
                && request.expectedCanonicalVectorHash().equals(request.handshake().canonicalVectorHash());
    }

    private boolean supports(RoleDirectoryIntegrationReadinessRequest request, String capability) {
        return request.handshake() != null && request.handshake().supports(capability);
    }

    private boolean fakeAllowed(RoleDirectoryIntegrationReadinessRequest request) {
        return !request.fakeAdapterPresent()
                || request.expectedEnvironment() == RoleDirectoryEnvironmentIdentity.LOCAL
                || request.expectedEnvironment() == RoleDirectoryEnvironmentIdentity.TEST;
    }

    private void check(boolean condition, RoleDirectoryIntegrationBlockReason reason,
            String passedName, List<RoleDirectoryIntegrationBlockReason> reasons, List<String> passed) {
        if (condition) passed.add(passedName); else reasons.add(reason);
    }

    private RoleDirectoryIntegrationEvidence evidence(
            RoleDirectoryIntegrationReadinessRequest request, List<String> passed) {
        RoleDirectoryEndpointDescriptor endpoint = request.actualEndpoint();
        RoleDirectoryContractHandshake handshake = request.handshake();
        return new RoleDirectoryIntegrationEvidence(Instant.now(clock), request.actualEnvironment(),
                endpoint == null ? "UNRESOLVED" : endpoint.providerCode(),
                endpoint == null ? "UNRESOLVED" : endpoint.serviceIdentity(),
                handshake == null ? "UNRESOLVED" : handshake.contractVersion(),
                handshake == null ? null : handshake.contractHash(),
                handshake == null ? "UNRESOLVED" : handshake.canonicalVersion(), passed,
                request.correlation());
    }
}
