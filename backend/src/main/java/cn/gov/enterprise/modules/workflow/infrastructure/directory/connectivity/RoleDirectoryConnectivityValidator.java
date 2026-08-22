package cn.gov.enterprise.modules.workflow.infrastructure.directory.connectivity;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCapabilityDescriptor;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEnvironmentIdentity;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryIntegrationReadiness;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Evaluates collected probe facts. It performs no network or member query itself. */
public final class RoleDirectoryConnectivityValidator {
    private final Clock clock;
    public RoleDirectoryConnectivityValidator() { this(Clock.systemUTC()); }
    RoleDirectoryConnectivityValidator(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public RoleDirectoryConnectivityResult validate(RoleDirectoryConnectivityRequest request) {
        Objects.requireNonNull(request, "request");
        List<RoleDirectoryConnectivityBlockReason> reasons = new ArrayList<>();
        reject(!allowed(request.expectedEnvironment()), RoleDirectoryConnectivityBlockReason.UNSUPPORTED_ENVIRONMENT, reasons);
        reject(request.expectedEndpoint() == null || !request.expectedEndpoint().equals(request.actualEndpoint()),
                RoleDirectoryConnectivityBlockReason.ENDPOINT_IDENTITY_MISMATCH, reasons);
        reject(!providerMatches(request), RoleDirectoryConnectivityBlockReason.PROVIDER_IDENTITY_MISMATCH, reasons);
        reject(!environmentMatches(request), RoleDirectoryConnectivityBlockReason.ENVIRONMENT_IDENTITY_MISMATCH, reasons);
        reject(request.secretSourceType() == null
                        || request.secretSourceType() == RoleDirectoryConnectivityRequest.SecretSourceType.MISSING
                        || !isHash(request.credentialIdentityHash()),
                RoleDirectoryConnectivityBlockReason.SECRET_SOURCE_INVALID, reasons);
        reject(!request.tlsEnabled() || !request.certificateValid()
                        || !request.hostnameVerificationEnabled() || request.tlsVersion() == null
                        || !isHash(request.certificateSubjectHash()) || request.certificateExpiry() == null
                        || !request.certificateExpiry().isAfter(Instant.now(clock)),
                RoleDirectoryConnectivityBlockReason.TLS_INVALID, reasons);
        reject(request.authenticationStatus() != RoleDirectoryConnectivityRequest.AuthenticationStatus.PASS,
                RoleDirectoryConnectivityBlockReason.AUTHENTICATION_FAILED, reasons);
        reject(!contractMatches(request), RoleDirectoryConnectivityBlockReason.CONTRACT_MISMATCH, reasons);
        reject(!canonicalMatches(request), RoleDirectoryConnectivityBlockReason.CANONICAL_MISMATCH, reasons);
        reject(!request.revisionCapabilityValid(), RoleDirectoryConnectivityBlockReason.REVISION_CAPABILITY_MISSING, reasons);
        reject(!request.historicalCapabilityValid(), RoleDirectoryConnectivityBlockReason.HISTORICAL_CAPABILITY_MISSING, reasons);
        reject(!request.completeSemanticsValid(), RoleDirectoryConnectivityBlockReason.COMPLETE_SEMANTICS_MISSING, reasons);
        reject(!request.paginationSemanticsValid(), RoleDirectoryConnectivityBlockReason.PAGINATION_SEMANTICS_MISSING, reasons);
        reject(!request.timeoutPolicyValid(), RoleDirectoryConnectivityBlockReason.TIMEOUT_POLICY_INVALID, reasons);
        reject(!request.retryPolicyValid() || request.attempts() <= 0 || request.attempts() > 4,
                RoleDirectoryConnectivityBlockReason.RETRY_POLICY_INVALID, reasons);
        reject(request.circuitState() != RoleDirectoryCircuitBreaker.State.CLOSED,
                RoleDirectoryConnectivityBlockReason.CIRCUIT_NOT_CLOSED, reasons);
        reject(request.selectedAdapterType() != RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION,
                RoleDirectoryConnectivityBlockReason.FAKE_ADAPTER_LEAK, reasons);
        reject(request.productionAdapterCount() != 1,
                RoleDirectoryConnectivityBlockReason.PRODUCTION_ADAPTER_NOT_UNIQUE, reasons);
        reject(!request.startupGatePassed(), RoleDirectoryConnectivityBlockReason.STARTUP_GATE_FAILED, reasons);
        reject(request.productionEndpointCalls() != 0 || request.productionSecretReads() != 0,
                RoleDirectoryConnectivityBlockReason.PRODUCTION_ISOLATION_VIOLATION, reasons);
        reject(request.roleMemberQueries() != 0 || request.candidateResolutions() != 0
                        || request.candidatePoolWrites() != 0 || request.taskCreates() != 0
                        || request.claimExecutions() != 0,
                RoleDirectoryConnectivityBlockReason.BUSINESS_QUERY_ISOLATION_VIOLATION, reasons);

        String outcome = reasons.isEmpty() ? "READY_FOR_INTEGRATION_TEST" : "BLOCKED";
        RoleDirectoryConnectivityEvidence evidence = evidence(request, outcome);
        RoleDirectoryConnectivityResult.Status status = reasons.isEmpty()
                ? RoleDirectoryConnectivityResult.Status.READY_FOR_INTEGRATION_TEST
                : RoleDirectoryConnectivityResult.Status.BLOCKED;
        return new RoleDirectoryConnectivityResult(status, reasons, evidence, false);
    }

    private boolean allowed(RoleDirectoryEnvironmentIdentity environment) {
        return environment == RoleDirectoryEnvironmentIdentity.TEST
                || environment == RoleDirectoryEnvironmentIdentity.PREPROD;
    }

    private boolean providerMatches(RoleDirectoryConnectivityRequest request) {
        return request.actualEndpoint() != null && request.handshake() != null
                && request.actualEndpoint().providerCode().equals(request.handshake().providerCode());
    }

    private boolean environmentMatches(RoleDirectoryConnectivityRequest request) {
        return request.actualEndpoint() != null && request.handshake() != null
                && request.expectedEnvironment() == request.actualEndpoint().environmentIdentity()
                && request.expectedEnvironment() == request.handshake().environmentIdentity();
    }

    private boolean contractMatches(RoleDirectoryConnectivityRequest request) {
        return request.handshake() != null
                && RoleDirectoryResolver.PORT_CONTRACT.equals(request.handshake().contractVersion())
                && RoleDirectoryResolver.CONTRACT_HASH.value().equals(request.handshake().contractHash());
    }

    private boolean canonicalMatches(RoleDirectoryConnectivityRequest request) {
        return request.handshake() != null
                && RoleDirectoryIntegrationReadiness.CANONICAL_VERSION.equals(request.handshake().canonicalVersion())
                && isHash(request.expectedCanonicalVectorHash())
                && request.expectedCanonicalVectorHash().equals(request.handshake().canonicalVectorHash());
    }

    private void reject(boolean condition, RoleDirectoryConnectivityBlockReason reason,
            List<RoleDirectoryConnectivityBlockReason> reasons) {
        if (condition) reasons.add(reason);
    }

    private boolean isHash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private RoleDirectoryConnectivityEvidence evidence(RoleDirectoryConnectivityRequest request, String outcome) {
        String providerCode = request.handshake() == null ? "UNRESOLVED" : request.handshake().providerCode();
        String providerVersion = request.handshake() == null ? "UNRESOLVED" : request.handshake().providerVersion();
        String endpointHash = request.actualEndpoint() == null ? "0".repeat(64)
                : RoleDirectoryConnectivityCanonical.hash(request.actualEndpoint().endpointIdentity().toString());
        String contractVersion = request.handshake() == null ? "UNRESOLVED" : request.handshake().contractVersion();
        String contractHash = request.handshake() == null ? "0".repeat(64) : request.handshake().contractHash();
        String canonicalVersion = request.handshake() == null ? "UNRESOLVED" : request.handshake().canonicalVersion();
        String tlsStatus = request.tlsEnabled() && request.certificateValid()
                && request.hostnameVerificationEnabled() ? "PASS" : "FAIL";
        String auth = request.authenticationStatus() == null ? "NOT_EXECUTED" : request.authenticationStatus().name();
        String canonical = String.join("\n", providerCode, providerVersion,
                String.valueOf(request.expectedEnvironment()), endpointHash, contractVersion, contractHash,
                canonicalVersion, tlsStatus, auth, Boolean.toString(request.revisionCapabilityValid()),
                Boolean.toString(request.historicalCapabilityValid()),
                Boolean.toString(request.completeSemanticsValid()), outcome);
        return new RoleDirectoryConnectivityEvidence(providerCode, providerVersion,
                request.expectedEnvironment(), endpointHash, contractVersion, contractHash,
                canonicalVersion, tlsStatus, auth, request.revisionCapabilityValid(),
                request.historicalCapabilityValid(), request.completeSemanticsValid(),
                Instant.now(clock), outcome, RoleDirectoryConnectivityCanonical.hash(canonical));
    }
}
