package cn.gov.enterprise.modules.workflow.infrastructure.directory.connectivity;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCapabilityDescriptor;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryContractHandshake;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEndpointDescriptor;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryEnvironmentIdentity;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness.RoleDirectoryIntegrationReadiness;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleDirectoryConnectivityValidatorTest {
    @Test
    void completeStubProbeMayReachIntegrationTestButNeverRuntimeEligibility() {
        var result = new RoleDirectoryConnectivityValidator().validate(new Fixture().request());
        assertThat(result.status()).isEqualTo(RoleDirectoryConnectivityResult.Status.READY_FOR_INTEGRATION_TEST);
        assertThat(result.blockReasons()).isEmpty();
        assertThat(result.resolverExecutionEligible()).isFalse();
        assertThat(result.evidence().evidenceHash()).matches("[0-9a-f]{64}");
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.enabled()).isFalse();
    }

    @Test
    void productionLocalIdentityAndProviderDriftMustBlock() {
        Fixture production = new Fixture(); production.environment = RoleDirectoryEnvironmentIdentity.PRODUCTION;
        production.endpoint = endpoint(production.environment, "APPROVAL_ROLE_DIRECTORY");
        production.handshake = handshake(production.environment, "APPROVAL_ROLE_DIRECTORY", "a".repeat(64));
        assertBlocked(production, RoleDirectoryConnectivityBlockReason.UNSUPPORTED_ENVIRONMENT);

        Fixture endpoint = new Fixture(); endpoint.actualEndpoint = endpoint(
                RoleDirectoryEnvironmentIdentity.PREPROD, "WRONG_PROVIDER");
        assertBlocked(endpoint, RoleDirectoryConnectivityBlockReason.ENDPOINT_IDENTITY_MISMATCH,
                RoleDirectoryConnectivityBlockReason.PROVIDER_IDENTITY_MISMATCH);

        Fixture environment = new Fixture(); environment.handshake = handshake(
                RoleDirectoryEnvironmentIdentity.TEST, "APPROVAL_ROLE_DIRECTORY", "a".repeat(64));
        assertBlocked(environment, RoleDirectoryConnectivityBlockReason.ENVIRONMENT_IDENTITY_MISMATCH);
    }

    @Test
    void securityHandshakeAndCapabilityFailuresMustFailClosed() {
        Fixture secret = new Fixture(); secret.secretSource = RoleDirectoryConnectivityRequest.SecretSourceType.MISSING;
        assertBlocked(secret, RoleDirectoryConnectivityBlockReason.SECRET_SOURCE_INVALID);
        Fixture tls = new Fixture(); tls.certificateValid = false;
        assertBlocked(tls, RoleDirectoryConnectivityBlockReason.TLS_INVALID);
        Fixture auth = new Fixture(); auth.auth = RoleDirectoryConnectivityRequest.AuthenticationStatus.FAIL;
        assertBlocked(auth, RoleDirectoryConnectivityBlockReason.AUTHENTICATION_FAILED);

        Fixture contract = new Fixture(); contract.handshake = new RoleDirectoryContractHandshake(
                "APPROVAL_ROLE_DIRECTORY", "PROVIDER_V1", RoleDirectoryEnvironmentIdentity.PREPROD,
                RoleDirectoryResolver.PORT_CONTRACT, "0".repeat(64),
                RoleDirectoryIntegrationReadiness.CANONICAL_VERSION, "a".repeat(64), capabilities());
        assertBlocked(contract, RoleDirectoryConnectivityBlockReason.CONTRACT_MISMATCH);
        Fixture canonical = new Fixture(); canonical.handshake = handshake(
                RoleDirectoryEnvironmentIdentity.PREPROD, "APPROVAL_ROLE_DIRECTORY", "b".repeat(64));
        assertBlocked(canonical, RoleDirectoryConnectivityBlockReason.CANONICAL_MISMATCH);

        Fixture revision = new Fixture(); revision.revision = false;
        assertBlocked(revision, RoleDirectoryConnectivityBlockReason.REVISION_CAPABILITY_MISSING);
        Fixture historical = new Fixture(); historical.historical = false;
        assertBlocked(historical, RoleDirectoryConnectivityBlockReason.HISTORICAL_CAPABILITY_MISSING);
        Fixture complete = new Fixture(); complete.complete = false;
        assertBlocked(complete, RoleDirectoryConnectivityBlockReason.COMPLETE_SEMANTICS_MISSING);
        Fixture pagination = new Fixture(); pagination.pagination = false;
        assertBlocked(pagination, RoleDirectoryConnectivityBlockReason.PAGINATION_SEMANTICS_MISSING);
    }

    @Test
    void retryCircuitAdapterAndIsolationFailuresMustFailClosed() {
        Fixture timeout = new Fixture(); timeout.timeout = false;
        assertBlocked(timeout, RoleDirectoryConnectivityBlockReason.TIMEOUT_POLICY_INVALID);
        Fixture retry = new Fixture(); retry.retry = false;
        assertBlocked(retry, RoleDirectoryConnectivityBlockReason.RETRY_POLICY_INVALID);
        Fixture open = new Fixture(); open.circuit = RoleDirectoryCircuitBreaker.State.OPEN;
        assertBlocked(open, RoleDirectoryConnectivityBlockReason.CIRCUIT_NOT_CLOSED);
        Fixture fake = new Fixture(); fake.adapterType = RoleDirectoryCapabilityDescriptor.AdapterType.FAKE;
        assertBlocked(fake, RoleDirectoryConnectivityBlockReason.FAKE_ADAPTER_LEAK);
        Fixture duplicate = new Fixture(); duplicate.adapterCount = 2;
        assertBlocked(duplicate, RoleDirectoryConnectivityBlockReason.PRODUCTION_ADAPTER_NOT_UNIQUE);
        Fixture startup = new Fixture(); startup = startup.withStartup(false);
        assertBlocked(startup, RoleDirectoryConnectivityBlockReason.STARTUP_GATE_FAILED);

        Fixture productionLeak = new Fixture(); productionLeak.productionCalls = 1;
        assertBlocked(productionLeak, RoleDirectoryConnectivityBlockReason.PRODUCTION_ISOLATION_VIOLATION);
        Fixture businessLeak = new Fixture(); businessLeak.memberQueries = 1;
        assertBlocked(businessLeak, RoleDirectoryConnectivityBlockReason.BUSINESS_QUERY_ISOLATION_VIOLATION);
    }

    @Test
    void connectivityEvidenceHashMustBeStableAndSensitiveToGovernedFacts() {
        var validator = new RoleDirectoryConnectivityValidator();
        String first = validator.validate(new Fixture().request()).evidence().evidenceHash();
        String second = validator.validate(new Fixture().request()).evidence().evidenceHash();
        Fixture changed = new Fixture(); changed.historical = false;
        String drifted = validator.validate(changed.request()).evidence().evidenceHash();
        assertThat(second).isEqualTo(first);
        assertThat(drifted).isNotEqualTo(first);
        assertThat(RoleDirectoryConnectivityEvidence.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("secret", "token", "password", "privateKey", "candidateUsers");
    }

    private static void assertBlocked(Fixture fixture, RoleDirectoryConnectivityBlockReason... reasons) {
        var result = new RoleDirectoryConnectivityValidator().validate(fixture.request());
        assertThat(result.status()).isEqualTo(RoleDirectoryConnectivityResult.Status.BLOCKED);
        assertThat(result.blockReasons()).contains(reasons);
        assertThat(result.resolverExecutionEligible()).isFalse();
    }

    private static RoleDirectoryEndpointDescriptor endpoint(
            RoleDirectoryEnvironmentIdentity environment, String provider) {
        return new RoleDirectoryEndpointDescriptor(provider, environment, "APPROVAL_ROLE_DIRECTORY_SERVICE",
                URI.create("https://directory.invalid/handshake"), RoleDirectoryResolver.PORT_CONTRACT,
                RoleDirectoryResolver.CONTRACT_HASH.value());
    }

    private static RoleDirectoryContractHandshake handshake(
            RoleDirectoryEnvironmentIdentity environment, String provider, String vectorHash) {
        return new RoleDirectoryContractHandshake(provider, "PROVIDER_V1", environment,
                RoleDirectoryResolver.PORT_CONTRACT, RoleDirectoryResolver.CONTRACT_HASH.value(),
                RoleDirectoryIntegrationReadiness.CANONICAL_VERSION, vectorHash, capabilities());
    }

    private static Set<String> capabilities() {
        return Set.of("AGGREGATE_REVISION", "HISTORICAL_EFFECTIVE_AT", "COMPLETE_RESULT");
    }

    private static final class Fixture {
        RoleDirectoryEnvironmentIdentity environment = RoleDirectoryEnvironmentIdentity.PREPROD;
        RoleDirectoryEndpointDescriptor endpoint = endpoint(environment, "APPROVAL_ROLE_DIRECTORY");
        RoleDirectoryEndpointDescriptor actualEndpoint = endpoint;
        RoleDirectoryContractHandshake handshake = handshake(environment, "APPROVAL_ROLE_DIRECTORY", "a".repeat(64));
        RoleDirectoryConnectivityRequest.SecretSourceType secretSource =
                RoleDirectoryConnectivityRequest.SecretSourceType.TEST_SECRET_MANAGER;
        boolean tls = true, certificateValid = true, hostname = true;
        RoleDirectoryConnectivityRequest.AuthenticationStatus auth =
                RoleDirectoryConnectivityRequest.AuthenticationStatus.PASS;
        boolean revision = true, historical = true, complete = true, pagination = true;
        boolean timeout = true, retry = true;
        RoleDirectoryCircuitBreaker.State circuit = RoleDirectoryCircuitBreaker.State.CLOSED;
        RoleDirectoryCapabilityDescriptor.AdapterType adapterType = RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION;
        int adapterCount = 1; boolean startup = true;
        long productionCalls, productionSecrets, memberQueries, candidateResolutions, poolWrites, taskCreates, claims;

        Fixture withStartup(boolean value) { startup = value; return this; }

        RoleDirectoryConnectivityRequest request() {
            return new RoleDirectoryConnectivityRequest(environment, endpoint, actualEndpoint, handshake,
                    "a".repeat(64), secretSource, "c".repeat(64), tls, certificateValid, hostname,
                    "TLSv1.3", "d".repeat(64), Instant.parse("2030-01-01T00:00:00Z"), auth,
                    revision, historical, complete, pagination, timeout, retry, 1, circuit, adapterType,
                    adapterCount, startup, productionCalls, productionSecrets, memberQueries,
                    candidateResolutions, poolWrites, taskCreates, claims);
        }
    }
}
