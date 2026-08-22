package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Prepared production adapter framework. This type is intentionally not a Spring component and
 * therefore cannot make ROLE_DIRECTORY_V1 executable by existing merely on the classpath.
 */
public final class ProductionRoleDirectoryAdapter implements RoleDirectoryPort {
    private final RoleDirectoryClient client;
    private final RoleDirectoryClientProperties properties;
    private final DirectoryCandidateLimitPolicy candidateLimit;
    private final RoleDirectoryCircuitBreaker circuitBreaker;
    private final RoleDirectoryMetricsPort metrics;
    private final RoleDirectoryAuditPort audit;

    public ProductionRoleDirectoryAdapter(
            RoleDirectoryClient client, RoleDirectoryClientProperties properties,
            DirectoryCandidateLimitPolicy candidateLimit, RoleDirectoryCircuitBreaker circuitBreaker,
            RoleDirectoryMetricsPort metrics, RoleDirectoryAuditPort audit) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.candidateLimit = Objects.requireNonNull(candidateLimit, "candidateLimit");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public RoleDirectoryResult resolve(RoleDirectoryQuery query) {
        return prepare(query).result();
    }

    public PreparedDirectoryResult prepare(RoleDirectoryQuery query) {
        Objects.requireNonNull(query, "query");
        if (!circuitBreaker.allowRequest()) {
            throw new DirectoryFailure(DirectoryFailure.Code.CIRCUIT_OPEN,
                    "role directory circuit is open; fallback is forbidden");
        }
        Instant started = Instant.now();
        metrics.recordEvent(RoleDirectoryMetricsPort.Metric.REQUEST_COUNT, 1);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                RoleDirectoryTransportResponse response = client.fetch(query);
                RoleDirectoryResult result = validate(query, response);
                circuitBreaker.recordSuccess();
                metrics.record("SUCCESS", Duration.between(started, Instant.now()),
                        distinctCandidateCount(result));
                metrics.recordEvent(RoleDirectoryMetricsPort.Metric.SUCCESS_COUNT, 1);
                metrics.recordEvent(RoleDirectoryMetricsPort.Metric.CANDIDATE_COUNT,
                        distinctCandidateCount(result));
                audit.append(new DirectoryResolutionAuditEvidence(queryHash(query), properties.providerCode(), result.revision(),
                        result.resultHash(), result.contractHash(), response.providerVersion(),
                        response.resolvedAt(), distinctCandidateCount(result), "SUCCESS"));
                return new PreparedDirectoryResult(result, DirectoryRevisionFence.freeze(query, result));
            } catch (DirectoryFailure failure) {
                circuitBreaker.recordFailure(failure);
                metrics.record(failure.code().name(), Duration.between(started, Instant.now()), 0);
                metrics.recordEvent(RoleDirectoryMetricsPort.Metric.FAILURE_COUNT, 1);
                if (failure.code() == DirectoryFailure.Code.NETWORK_TIMEOUT) {
                    metrics.recordEvent(RoleDirectoryMetricsPort.Metric.TIMEOUT_COUNT, 1);
                }
                audit.append(new DirectoryResolutionAuditEvidence(queryHash(query),
                        properties.providerCode(), 0, null, properties.expectedContractHash(),
                        "UNRESOLVED", Instant.now(), 0, failure.code().name()));
                if (failure.code().retryable() && attempt <= properties.maxRetry()) {
                    metrics.recordEvent(RoleDirectoryMetricsPort.Metric.RETRY_COUNT, 1);
                }
                if (!failure.code().retryable() || attempt > properties.maxRetry()) throw failure;
            }
        }
    }

    public void verify(DirectoryRevisionFence fence, RoleDirectoryQuery query) {
        Objects.requireNonNull(fence, "fence");
        fence.verify(query, prepare(query).result());
    }

    private RoleDirectoryResult validate(RoleDirectoryQuery query, RoleDirectoryTransportResponse response) {
        if (response == null) throw failure(DirectoryFailure.Code.TEMPORARY_UNAVAILABLE,
                "directory returned no response");
        if (!properties.expectedContractVersion().equals(response.contractVersion())
                || !properties.expectedContractHash().equals(response.contractHash())
                || !RoleDirectoryResolver.PORT_CONTRACT.equals(response.contractVersion())) {
            throw failure(DirectoryFailure.Code.CONTRACT_MISMATCH,
                    "remote directory contract does not exactly match the frozen local contract");
        }
        if (!response.complete()) throw failure(DirectoryFailure.Code.PARTIAL_RESULT,
                "partial directory response is forbidden");
        if (!query.enterpriseId().equals(response.enterpriseId())
                || !query.organizationId().equals(response.organizationId())
                || !query.roleCode().equals(response.roleCode())
                || !query.effectiveAt().equals(response.effectiveAt())) {
            throw failure(DirectoryFailure.Code.ROLE_ORG_MISMATCH,
                    "directory response scope differs from the immutable query");
        }
        if (response.revision() <= 0 || response.providerVersion() == null
                || response.providerVersion().isBlank() || response.resolvedAt() == null) {
            throw failure(DirectoryFailure.Code.REVISION_MISMATCH,
                    "revision and provider evidence must be complete");
        }
        verifyMemberEvidence(response);
        RoleDirectoryResult result;
        try {
            result = new RoleDirectoryResult(response.roleCode(), response.organizationId(),
                    response.effectiveAt(), response.revision(), true, response.members(),
                    response.resultHash(), response.contractHash(), response.source());
        } catch (RuntimeException exception) {
            throw new DirectoryFailure(DirectoryFailure.Code.SOURCE_CONFLICT,
                    "invalid directory member evidence", exception);
        }
        if (!result.hasValidHash()) throw failure(DirectoryFailure.Code.HASH_MISMATCH,
                "remote result hash differs from ROLE_CANONICAL_JSON_V1 recomputation");
        int count = distinctCandidateCount(result);
        if (count == 0) throw failure(DirectoryFailure.Code.ROLE_NOT_FOUND,
                "no effective role member exists");
        candidateLimit.verify(count);
        return result;
    }

    private void verifyMemberEvidence(RoleDirectoryTransportResponse response) {
        Map<String, RoleDirectoryMember> assignments = new HashMap<>();
        for (RoleDirectoryMember member : response.members()) {
            if (!response.roleCode().equals(member.roleCode())
                    || !response.organizationId().equals(member.organizationId())) {
                throw failure(DirectoryFailure.Code.SOURCE_CONFLICT,
                        "member evidence crosses role or organization scope");
            }
            RoleDirectoryMember previous = assignments.putIfAbsent(member.assignmentId(), member);
            if (previous != null) throw failure(DirectoryFailure.Code.SOURCE_CONFLICT,
                    "duplicate assignment evidence is forbidden");
        }
    }

    private int distinctCandidateCount(RoleDirectoryResult result) {
        return (int) result.members().stream().filter(member -> member.effectiveAt(result.effectiveAt()))
                .map(RoleDirectoryMember::userId).distinct().count();
    }

    private String queryHash(RoleDirectoryQuery query) {
        String canonical = query.enterpriseId() + "\n" + query.organizationId() + "\n"
                + query.roleCode() + "\n" + query.effectiveAt() + "\n" + query.contractVersion();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private DirectoryFailure failure(DirectoryFailure.Code code, String message) {
        return new DirectoryFailure(code, message);
    }

    public record PreparedDirectoryResult(RoleDirectoryResult result, DirectoryRevisionFence fence) { }
}
