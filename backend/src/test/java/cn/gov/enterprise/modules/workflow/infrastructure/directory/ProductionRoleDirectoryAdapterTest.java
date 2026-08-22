package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectorySourceType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductionRoleDirectoryAdapterTest {
    private static final Instant AT = Instant.parse("2026-08-18T01:02:03.004Z");
    private static final String ROLE = "INVESTMENT_FINANCE_REVIEWER";
    private static final String ORG = "ORG-001";

    @Test
    void completeEvidenceMustCreateExactFenceAndRemainOrderStable() {
        RoleDirectoryResult result = result(List.of(member("USER-002", "A-2"), member("USER-001", "A-1")));
        ProductionRoleDirectoryAdapter adapter = adapter(query -> response(result), 0, 10);
        var prepared = adapter.prepare(query());

        assertThat(prepared.result().members()).extracting(RoleDirectoryMember::userId)
                .containsExactly("USER-001", "USER-002");
        assertThat(prepared.fence().directoryRevision()).isEqualTo(7);
        prepared.fence().verify(query(), prepared.result());

        RoleDirectoryResult reversed = result(List.of(member("USER-001", "A-1"), member("USER-002", "A-2")));
        assertThat(reversed.resultHash()).isEqualTo(result.resultHash());
    }

    @Test
    void retryIsBoundedAndNonRetryableFailureIsNotRetried() {
        AtomicInteger transientCalls = new AtomicInteger();
        ProductionRoleDirectoryAdapter recovered = adapter(query -> {
            if (transientCalls.getAndIncrement() == 0) throw failure(DirectoryFailure.Code.NETWORK_TIMEOUT);
            return response(result(List.of(member("USER-001", "A-1"))));
        }, 1, 10);
        assertThat(recovered.resolve(query()).members()).hasSize(1);
        assertThat(transientCalls).hasValue(2);

        AtomicInteger contractCalls = new AtomicInteger();
        ProductionRoleDirectoryAdapter rejected = adapter(query -> {
            contractCalls.incrementAndGet();
            RoleDirectoryTransportResponse valid = response(result(List.of(member("USER-001", "A-1"))));
            return copy(valid, true, "WRONG_CONTRACT", valid.contractHash(), valid.resultHash(), valid.revision(), valid.members());
        }, 3, 10);
        assertFailure(rejected, DirectoryFailure.Code.CONTRACT_MISMATCH);
        assertThat(contractCalls).hasValue(1);
    }

    @Test
    void partialHashRevisionEmptyDuplicateAndOverflowMustFailClosed() {
        RoleDirectoryResult one = result(List.of(member("USER-001", "A-1")));
        RoleDirectoryTransportResponse valid = response(one);
        assertFailure(adapter(q -> copy(valid, false, valid.contractVersion(), valid.contractHash(),
                valid.resultHash(), valid.revision(), valid.members()), 0, 10), DirectoryFailure.Code.PARTIAL_RESULT);
        assertFailure(adapter(q -> copy(valid, true, valid.contractVersion(), valid.contractHash(),
                "0".repeat(64), valid.revision(), valid.members()), 0, 10), DirectoryFailure.Code.HASH_MISMATCH);
        assertFailure(adapter(q -> copy(valid, true, valid.contractVersion(), "0".repeat(64),
                valid.resultHash(), valid.revision(), valid.members()), 0, 10), DirectoryFailure.Code.CONTRACT_MISMATCH);

        RoleDirectoryResult empty = result(List.of());
        assertFailure(adapter(q -> response(empty), 0, 10), DirectoryFailure.Code.ROLE_NOT_FOUND);
        assertFailure(adapter(q -> response(result(List.of(member("USER-001", "A-1"),
                member("USER-002", "A-2")))), 0, 1), DirectoryFailure.Code.CANDIDATE_LIMIT_EXCEEDED);
        assertFailure(adapter(q -> response(result(List.of(member("USER-001", "A-1"),
                member("USER-002", "A-1")))), 0, 10), DirectoryFailure.Code.SOURCE_CONFLICT);

        var prepared = adapter(q -> valid, 0, 10).prepare(query());
        RoleDirectoryResult revisionChanged = new RoleDirectoryResult(one.roleCode(), one.organizationId(),
                one.effectiveAt(), 8, true, one.members(), one.resultHash(), one.contractHash(), one.source());
        assertThatThrownBy(() -> prepared.fence().verify(query(), revisionChanged))
                .isInstanceOf(DirectoryFailure.class)
                .extracting(error -> ((DirectoryFailure) error).code())
                .isEqualTo(DirectoryFailure.Code.REVISION_MISMATCH);
    }

    @Test
    void openCircuitMustBlockWithoutFallback() {
        InMemoryRoleDirectoryCircuitBreaker breaker = new InMemoryRoleDirectoryCircuitBreaker(1);
        breaker.recordFailure(failure(DirectoryFailure.Code.NETWORK_TIMEOUT));
        ProductionRoleDirectoryAdapter adapter = new ProductionRoleDirectoryAdapter(
                q -> response(result(List.of(member("USER-001", "A-1")))), properties(0),
                new DirectoryCandidateLimitPolicy(10, 10), breaker,
                RoleDirectoryMetricsPort.noop(), RoleDirectoryAuditPort.noop());
        assertFailure(adapter, DirectoryFailure.Code.CIRCUIT_OPEN);
    }

    @Test
    void failureMustEmitPiiFreeAuditAndGovernedMetrics() {
        List<DirectoryResolutionAuditEvidence> evidence = new ArrayList<>();
        List<RoleDirectoryMetricsPort.Metric> events = new ArrayList<>();
        RoleDirectoryMetricsPort metrics = new RoleDirectoryMetricsPort() {
            public void record(String outcome, Duration latency, int candidateCount) { }
            public void recordEvent(Metric metric, long value) { events.add(metric); }
        };
        ProductionRoleDirectoryAdapter adapter = new ProductionRoleDirectoryAdapter(
                query -> { throw failure(DirectoryFailure.Code.NETWORK_TIMEOUT); }, properties(0),
                new DirectoryCandidateLimitPolicy(10, 10),
                new InMemoryRoleDirectoryCircuitBreaker(10), metrics, evidence::add);

        assertFailure(adapter, DirectoryFailure.Code.NETWORK_TIMEOUT);
        assertThat(events).contains(RoleDirectoryMetricsPort.Metric.REQUEST_COUNT,
                RoleDirectoryMetricsPort.Metric.FAILURE_COUNT,
                RoleDirectoryMetricsPort.Metric.TIMEOUT_COUNT);
        assertThat(evidence).singleElement().satisfies(item -> {
            assertThat(item.providerCode()).isEqualTo("APPROVAL_ROLE_DIRECTORY");
            assertThat(item.outcome()).isEqualTo("NETWORK_TIMEOUT");
            assertThat(item.queryHash()).matches("[0-9a-f]{64}");
        });
    }

    private static ProductionRoleDirectoryAdapter adapter(RoleDirectoryClient client, int retry, int limit) {
        return new ProductionRoleDirectoryAdapter(client, properties(retry),
                new DirectoryCandidateLimitPolicy(limit, limit),
                new InMemoryRoleDirectoryCircuitBreaker(10), RoleDirectoryMetricsPort.noop(),
                RoleDirectoryAuditPort.noop());
    }

    static RoleDirectoryClientProperties properties(int retry) {
        return new RoleDirectoryClientProperties(URI.create("http://127.0.0.1:18080/roles"),
                Duration.ofMillis(100), Duration.ofMillis(200), retry, "test", "APPROVAL_ROLE_DIRECTORY",
                RoleDirectoryResolver.PORT_CONTRACT, RoleDirectoryResolver.CONTRACT_HASH.value());
    }

    static RoleDirectoryQuery query() {
        return new RoleDirectoryQuery("ENT-001", ORG, ROLE, AT,
                RoleDirectoryResolver.PORT_CONTRACT, "trace-1");
    }

    static RoleDirectoryMember member(String user, String assignment) {
        return new RoleDirectoryMember(user, assignment, ROLE, ORG,
                Instant.parse("2026-01-01T00:00:00Z"), null,
                RoleDirectorySourceType.MANUAL_GOVERNANCE, "SRC-" + assignment, 7);
    }

    static RoleDirectoryResult result(List<RoleDirectoryMember> members) {
        return RoleDirectoryResult.complete(ROLE, ORG, AT, 7, members,
                RoleDirectoryResolver.CONTRACT_HASH.value(), "PROVIDER_TEST");
    }

    static RoleDirectoryTransportResponse response(RoleDirectoryResult result) {
        return new RoleDirectoryTransportResponse("ENT-001", result.organizationId(), result.roleCode(),
                result.effectiveAt(), result.revision(), result.complete(), result.members(), result.resultHash(),
                RoleDirectoryResolver.PORT_CONTRACT, result.contractHash(), "PROVIDER_V1", AT, result.source());
    }

    static RoleDirectoryTransportResponse copy(RoleDirectoryTransportResponse value, boolean complete,
            String contractVersion, String contractHash, String resultHash, long revision,
            List<RoleDirectoryMember> members) {
        return new RoleDirectoryTransportResponse(value.enterpriseId(), value.organizationId(), value.roleCode(),
                value.effectiveAt(), revision, complete, members, resultHash, contractVersion, contractHash,
                value.providerVersion(), value.resolvedAt(), value.source());
    }

    private static DirectoryFailure failure(DirectoryFailure.Code code) {
        return new DirectoryFailure(code, code.name());
    }

    private static void assertFailure(ProductionRoleDirectoryAdapter adapter, DirectoryFailure.Code code) {
        assertThatThrownBy(() -> adapter.resolve(query())).isInstanceOf(DirectoryFailure.class)
                .extracting(error -> ((DirectoryFailure) error).code()).isEqualTo(code);
    }
}
