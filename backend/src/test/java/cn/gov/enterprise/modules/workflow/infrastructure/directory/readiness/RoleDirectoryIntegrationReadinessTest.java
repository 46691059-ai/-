package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.infrastructure.directory.RoleDirectoryCircuitBreaker;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleDirectoryIntegrationReadinessTest {
    @Test
    void completeEvidenceMayOnlyReachConnectivityReadiness() {
        var result = new RoleDirectoryIntegrationReadiness().evaluate(new Fixture().request());
        assertThat(result.status()).isEqualTo(
                RoleDirectoryIntegrationReadinessResult.Status.READY_FOR_CONNECTIVITY_TEST);
        assertThat(result.blockReasons()).isEmpty();
        assertThat(result.resolverExecutionEligible()).isFalse();
        assertThat(result.evidence().passedChecks()).hasSize(24);
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.enabled()).isFalse();
    }

    @Test
    void identityContractAndCanonicalDriftMustBlock() {
        Fixture environment = new Fixture(); environment.actualEnvironment = RoleDirectoryEnvironmentIdentity.TEST;
        assertBlocked(environment, RoleDirectoryIntegrationBlockReason.ENVIRONMENT_MISMATCH);

        Fixture endpoint = new Fixture(); endpoint.actualEndpoint = endpoint("OTHER_PROVIDER", RoleDirectoryEnvironmentIdentity.PREPROD);
        assertBlocked(endpoint, RoleDirectoryIntegrationBlockReason.ENDPOINT_IDENTITY_MISMATCH,
                RoleDirectoryIntegrationBlockReason.PROVIDER_MISMATCH);

        Fixture contract = new Fixture(); contract.handshake = handshake("0".repeat(64), "a".repeat(64));
        assertBlocked(contract, RoleDirectoryIntegrationBlockReason.CONTRACT_MISMATCH);

        Fixture canonical = new Fixture(); canonical.handshake = handshake(
                RoleDirectoryResolver.CONTRACT_HASH.value(), "b".repeat(64));
        assertBlocked(canonical, RoleDirectoryIntegrationBlockReason.CANONICAL_MISMATCH);
    }

    @Test
    void providerDataCapabilitiesMustBeExplicitAndAtomic() {
        Fixture revision = new Fixture(); revision.aggregateRevisionMonotonic = false;
        assertBlocked(revision, RoleDirectoryIntegrationBlockReason.REVISION_CAPABILITY_UNSUPPORTED);
        Fixture historical = new Fixture(); historical.historical = false;
        assertBlocked(historical, RoleDirectoryIntegrationBlockReason.HISTORICAL_QUERY_UNSUPPORTED);
        Fixture complete = new Fixture(); complete.complete = false;
        assertBlocked(complete, RoleDirectoryIntegrationBlockReason.COMPLETE_SEMANTICS_UNSUPPORTED);
        Fixture pagination = new Fixture(); pagination.pagination = false;
        assertBlocked(pagination, RoleDirectoryIntegrationBlockReason.PAGINATION_INCOMPLETE);
        Fixture limit = new Fixture(); limit.candidateLimit = false;
        assertBlocked(limit, RoleDirectoryIntegrationBlockReason.CANDIDATE_LIMIT_UNGOVERNED);
        Fixture sla = new Fixture(); sla.sla = false;
        assertBlocked(sla, RoleDirectoryIntegrationBlockReason.SLA_PLAN_MISSING);
    }

    @Test
    void transportSecurityAndCircuitMustFailClosed() {
        Fixture secret = new Fixture(); secret.secret = false;
        assertBlocked(secret, RoleDirectoryIntegrationBlockReason.SECRET_PROVIDER_MISSING);
        Fixture tls = new Fixture(); tls.tls = false;
        assertBlocked(tls, RoleDirectoryIntegrationBlockReason.TLS_CAPABILITY_MISSING);
        Fixture config = new Fixture(); config.transport = false;
        assertBlocked(config, RoleDirectoryIntegrationBlockReason.TRANSPORT_CONFIGURATION_INVALID);
        Fixture open = new Fixture(); open.circuit = RoleDirectoryCircuitBreaker.State.OPEN;
        assertBlocked(open, RoleDirectoryIntegrationBlockReason.CIRCUIT_NOT_CLOSED);
        Fixture halfOpen = new Fixture(); halfOpen.circuit = RoleDirectoryCircuitBreaker.State.HALF_OPEN;
        assertBlocked(halfOpen, RoleDirectoryIntegrationBlockReason.CIRCUIT_NOT_CLOSED);
    }

    @Test
    void productionIsolationOwnershipAndSecurityAreP0Gates() {
        Fixture fake = new Fixture(); fake.fake = true;
        assertBlocked(fake, RoleDirectoryIntegrationBlockReason.FAKE_ADAPTER_FORBIDDEN);
        Fixture duplicate = new Fixture(); duplicate.productionAdapters = 2;
        assertBlocked(duplicate, RoleDirectoryIntegrationBlockReason.PRODUCTION_ADAPTER_NOT_UNIQUE);
        Fixture owner = new Fixture(); owner.ownership = new RoleDirectoryOwnershipDescriptor(null, "D", "O", "T", "S");
        assertBlocked(owner, RoleDirectoryIntegrationBlockReason.OWNERSHIP_MISSING);
        Fixture raci = new Fixture(); raci.raci = new RoleDirectoryRaciApproval(true, true, true, false);
        assertBlocked(raci, RoleDirectoryIntegrationBlockReason.RACI_APPROVAL_MISSING);
        Fixture security = new Fixture(); security.security = new RoleDirectorySecurityReadiness(
                true, true, false, true, true, true, true, true);
        assertBlocked(security, RoleDirectoryIntegrationBlockReason.SECURITY_REQUIREMENT_MISSING);
    }

    @Test
    void observabilityPiiAndDependencyCapabilitiesAreMandatory() {
        Fixture metrics = new Fixture(); metrics.metrics = false;
        assertBlocked(metrics, RoleDirectoryIntegrationBlockReason.METRICS_CAPABILITY_MISSING);
        Fixture audit = new Fixture(); audit.audit = false;
        assertBlocked(audit, RoleDirectoryIntegrationBlockReason.AUDIT_CAPABILITY_MISSING);
        Fixture pii = new Fixture(); pii.pii = false;
        assertBlocked(pii, RoleDirectoryIntegrationBlockReason.PII_POLICY_MISSING);
        Fixture dependencies = new Fixture(); dependencies.dependencies = false;
        assertBlocked(dependencies, RoleDirectoryIntegrationBlockReason.PRODUCTION_DEPENDENCY_VIOLATION);
    }

    @Test
    void fakeIsAllowedOnlyInLocalAndTestReadiness() {
        Fixture local = new Fixture();
        local.expectedEnvironment = RoleDirectoryEnvironmentIdentity.LOCAL;
        local.actualEnvironment = RoleDirectoryEnvironmentIdentity.LOCAL;
        local.expectedEndpoint = endpoint("APPROVAL_ROLE_DIRECTORY", RoleDirectoryEnvironmentIdentity.LOCAL);
        local.actualEndpoint = local.expectedEndpoint;
        local.handshake = handshake(RoleDirectoryResolver.CONTRACT_HASH.value(), "a".repeat(64),
                RoleDirectoryEnvironmentIdentity.LOCAL);
        local.fake = true;
        assertThat(new RoleDirectoryIntegrationReadiness().evaluate(local.request()).blockReasons())
                .doesNotContain(RoleDirectoryIntegrationBlockReason.FAKE_ADAPTER_FORBIDDEN);
    }

    private static void assertBlocked(Fixture fixture, RoleDirectoryIntegrationBlockReason... reasons) {
        var result = new RoleDirectoryIntegrationReadiness().evaluate(fixture.request());
        assertThat(result.status()).isEqualTo(RoleDirectoryIntegrationReadinessResult.Status.BLOCKED);
        assertThat(result.blockReasons()).contains(reasons);
        assertThat(result.resolverExecutionEligible()).isFalse();
    }

    private static RoleDirectoryEndpointDescriptor endpoint(
            String provider, RoleDirectoryEnvironmentIdentity environment) {
        return new RoleDirectoryEndpointDescriptor(provider, environment, "APPROVAL_ROLE_DIRECTORY_SERVICE",
                URI.create("https://directory.invalid/roles"), RoleDirectoryResolver.PORT_CONTRACT,
                RoleDirectoryResolver.CONTRACT_HASH.value());
    }

    private static RoleDirectoryContractHandshake handshake(String contractHash, String vectorHash) {
        return handshake(contractHash, vectorHash, RoleDirectoryEnvironmentIdentity.PREPROD);
    }

    private static RoleDirectoryContractHandshake handshake(String contractHash, String vectorHash,
            RoleDirectoryEnvironmentIdentity environment) {
        return new RoleDirectoryContractHandshake("APPROVAL_ROLE_DIRECTORY", "PROVIDER_V1", environment,
                RoleDirectoryResolver.PORT_CONTRACT, contractHash,
                RoleDirectoryIntegrationReadiness.CANONICAL_VERSION, vectorHash,
                Set.of("AGGREGATE_REVISION", "HISTORICAL_EFFECTIVE_AT", "COMPLETE_RESULT"));
    }

    private static final class Fixture {
        RoleDirectoryEnvironmentIdentity expectedEnvironment = RoleDirectoryEnvironmentIdentity.PREPROD;
        RoleDirectoryEnvironmentIdentity actualEnvironment = RoleDirectoryEnvironmentIdentity.PREPROD;
        RoleDirectoryEndpointDescriptor expectedEndpoint = endpoint("APPROVAL_ROLE_DIRECTORY", expectedEnvironment);
        RoleDirectoryEndpointDescriptor actualEndpoint = expectedEndpoint;
        RoleDirectoryContractHandshake handshake = handshake(
                RoleDirectoryResolver.CONTRACT_HASH.value(), "a".repeat(64));
        boolean secret = true, tls = true, transport = true, aggregateRevisionMonotonic = true;
        boolean historical = true, complete = true, pagination = true, candidateLimit = true, sla = true;
        RoleDirectoryCircuitBreaker.State circuit = RoleDirectoryCircuitBreaker.State.CLOSED;
        boolean fake = false; int productionAdapters = 1;
        RoleDirectoryOwnershipDescriptor ownership = new RoleDirectoryOwnershipDescriptor("B", "D", "O", "T", "S");
        RoleDirectoryRaciApproval raci = new RoleDirectoryRaciApproval(true, true, true, true);
        RoleDirectorySecurityReadiness security = new RoleDirectorySecurityReadiness(
                true, true, true, true, true, true, true, true);
        boolean metrics = true, audit = true, pii = true, dependencies = true;

        RoleDirectoryIntegrationReadinessRequest request() {
            return new RoleDirectoryIntegrationReadinessRequest(expectedEnvironment, actualEnvironment,
                    expectedEndpoint, actualEndpoint, handshake, "a".repeat(64), secret, tls, transport,
                    aggregateRevisionMonotonic, historical, complete, pagination, candidateLimit, sla,
                    circuit, fake, productionAdapters, ownership, raci, security, metrics, audit, pii,
                    dependencies, new RoleDirectoryCorrelation("corr-1", "dir-1", "admission-1"));
        }
    }
}
