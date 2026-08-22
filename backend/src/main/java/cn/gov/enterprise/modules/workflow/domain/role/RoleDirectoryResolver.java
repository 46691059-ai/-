package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.Objects;

/** Prepared ROLE resolver. It validates directory evidence but does not create a CandidatePool. */
public final class RoleDirectoryResolver {
    public static final ResolverCode CODE = ResolverCode.of("ROLE_DIRECTORY");
    public static final ResolverVersion VERSION = ResolverVersion.of("ROLE_DIRECTORY_V1");
    public static final String PORT_CONTRACT = "ROLE_DIRECTORY_PORT_V1";
    public static final ResolverContractHash CONTRACT_HASH = ResolverContractHash.of(
            "5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d");
    public static final AssignmentResolverDescriptor PREPARED_DESCRIPTOR =
            new AssignmentResolverDescriptor(CODE, VERSION, AssignmentStrategy.Type.ROLE,
                    ResolverMode.CANDIDATE_POOL, CONTRACT_HASH,
                    ResolverStatus.PREPARED, false);

    private final RoleDirectoryPort directory;
    private final RoleDirectoryRetryPolicy retryPolicy;

    public RoleDirectoryResolver(RoleDirectoryPort directory) {
        this(directory, new RoleDirectoryRetryPolicy());
    }

    RoleDirectoryResolver(RoleDirectoryPort directory, RoleDirectoryRetryPolicy retryPolicy) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    public RoleResolverResult resolve(RoleResolverContext context) {
        RoleDirectoryResult result = resolveDirectory(context);
        var candidates = result.members().stream()
                .filter(member -> member.roleCode().equals(context.roleCode()))
                .filter(member -> member.organizationId().equals(context.businessOrgId()))
                .filter(member -> member.effectiveAt(context.effectiveAt()))
                .map(RoleDirectoryMember::userId).distinct().sorted().toList();
        return new RoleResolverResult(candidates, result.revision(), result.resultHash(),
                CONTRACT_HASH.value());
    }

    /** Returns validated atomic evidence for the non-persistent WF5.2 adapter. */
    public RoleDirectoryResult resolveDirectory(RoleResolverContext context) {
        Objects.requireNonNull(context, "context");
        validateBinding(context);
        RoleDirectoryQuery query = new RoleDirectoryQuery(context.enterpriseId(),
                context.businessOrgId(), context.roleCode(), context.effectiveAt(),
                PORT_CONTRACT, context.traceId());
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return validateResult(query, directory.resolve(query));
            } catch (RoleDirectoryException exception) {
                if (!retryPolicy.mayRetry(exception.errorCode(), attempts)) throw exception;
            }
        }
    }

    private void validateBinding(RoleResolverContext context) {
        if (!CODE.equals(context.resolverBinding().resolverCode())
                || !VERSION.equals(context.resolverBinding().resolverVersion())
                || !CONTRACT_HASH.equals(context.resolverBinding().contractHash())) {
            throw failure(RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH,
                    "ROLE resolver binding does not match the frozen contract");
        }
    }

    private RoleDirectoryResult validateResult(RoleDirectoryQuery query, RoleDirectoryResult result) {
        if (result == null) throw failure(RoleDirectoryErrorCode.DIRECTORY_UNAVAILABLE,
                "directory returned no response");
        if (!result.complete()) throw failure(RoleDirectoryErrorCode.DIRECTORY_PARTIAL_RESULT,
                "partial directory response is forbidden");
        if (!query.roleCode().equals(result.roleCode())
                || !query.organizationId().equals(result.organizationId())
                || !query.effectiveAt().equals(result.effectiveAt())) {
            throw failure(RoleDirectoryErrorCode.ROLE_ORG_MISMATCH,
                    "directory response does not match the query scope");
        }
        if (!CONTRACT_HASH.value().equals(result.contractHash()) || !result.hasValidHash()) {
            throw failure(RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH,
                    "directory contract or result hash mismatch");
        }
        var candidates = result.members().stream()
                .filter(member -> member.roleCode().equals(query.roleCode()))
                .filter(member -> member.organizationId().equals(query.organizationId()))
                .filter(member -> member.effectiveAt(query.effectiveAt()))
                .map(RoleDirectoryMember::userId).distinct().sorted().toList();
        if (candidates.isEmpty()) throw failure(RoleDirectoryErrorCode.NO_ROLE_MEMBER,
                "no effective approval-role member exists");
        return result;
    }

    private RoleDirectoryException failure(RoleDirectoryErrorCode code, String message) {
        return new RoleDirectoryException(code, message);
    }
}
