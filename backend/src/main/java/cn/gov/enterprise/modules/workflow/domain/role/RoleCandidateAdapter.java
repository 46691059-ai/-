package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;

/** Converts validated directory evidence into a non-persistent Candidate Pool draft. */
public final class RoleCandidateAdapter {
    public static final ResolverCode CODE = ResolverCode.of("ROLE_CANDIDATE_ADAPTER");
    public static final ResolverVersion VERSION = ResolverVersion.of("ROLE_CANDIDATE_ADAPTER_V1");
    public static final ResolverContractHash CONTRACT_HASH = ResolverContractHash.sha256(
            "ROLE_CANDIDATE_ADAPTER|ROLE_CANDIDATE_ADAPTER_V1|ROLE|ROLE_POOL_PREVIEW|"
                    + "ROLE_CANDIDATE_CANONICAL_V1|NO_PERSISTENCE|NO_RUNTIME");
    public static final AssignmentResolverDescriptor PREPARED_DESCRIPTOR =
            new AssignmentResolverDescriptor(CODE, VERSION, AssignmentStrategy.Type.ROLE,
                    ResolverMode.CANDIDATE_POOL, CONTRACT_HASH,
                    ResolverStatus.PREPARED, false);

    private final RoleCandidatePoolDraftBuilder draftBuilder;

    public RoleCandidateAdapter(int candidateLimit) {
        this.draftBuilder = new RoleCandidatePoolDraftBuilder(candidateLimit);
    }

    public RoleCandidateResult adapt(RoleCandidateContext context, RoleDirectoryResult directory) {
        return adapt(context, directory, directory.revision());
    }

    /** expectedRevision is frozen by the caller across bounded directory retries. */
    public RoleCandidateResult adapt(
            RoleCandidateContext context, RoleDirectoryResult directory, long expectedRevision) {
        validate(context, directory, expectedRevision);
        return draftBuilder.build(context, directory);
    }

    private void validate(RoleCandidateContext context, RoleDirectoryResult directory,
            long expectedRevision) {
        if (context == null || directory == null) throw new IllegalArgumentException("context and directory are required");
        if (!context.resolverBinding().resolverCode().equals(RoleDirectoryResolver.CODE)
                || !context.resolverBinding().resolverVersion().equals(RoleDirectoryResolver.VERSION)
                || !context.resolverBinding().contractHash().equals(RoleDirectoryResolver.CONTRACT_HASH)
                || !directory.contractHash().equals(RoleDirectoryResolver.CONTRACT_HASH.value())) {
            throw failure(RoleCandidateErrorCode.ROLE_DIRECTORY_CONTRACT_INVALID,
                    "directory or binding contract mismatch");
        }
        if (expectedRevision <= 0 || directory.revision() != expectedRevision) {
            throw failure(RoleCandidateErrorCode.ROLE_DIRECTORY_REVISION_INVALID,
                    "directory revision drift detected");
        }
        if (!context.roleCode().equals(directory.roleCode())
                || !context.organizationId().equals(directory.organizationId())
                || !context.effectiveAt().equals(directory.effectiveAt())) {
            throw failure(RoleCandidateErrorCode.ROLE_DIRECTORY_REVISION_INVALID,
                    "directory evidence does not match candidate context");
        }
        if (!directory.complete() || !directory.hasValidHash()) {
            throw failure(RoleCandidateErrorCode.ROLE_RESULT_HASH_INVALID,
                    "directory result must be complete and hash-valid");
        }
    }

    private RoleCandidateException failure(RoleCandidateErrorCode code, String message) {
        return new RoleCandidateException(code, message);
    }
}
