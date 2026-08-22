package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds a pure domain preview; no workflow_candidate_pool row is created. */
public final class RoleCandidatePoolDraftBuilder {
    private final int candidateLimit;

    public RoleCandidatePoolDraftBuilder(int candidateLimit) {
        if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be positive");
        this.candidateLimit = candidateLimit;
    }

    public RoleCandidateResult build(RoleCandidateContext context, RoleDirectoryResult directory) {
        List<RoleDirectoryMember> effectiveMembers = directory.members().stream()
                .filter(member -> member.roleCode().equals(context.roleCode()))
                .filter(member -> member.organizationId().equals(context.organizationId()))
                .filter(member -> member.effectiveAt(context.effectiveAt())).toList();
        if (effectiveMembers.isEmpty()) throw failure(RoleCandidateErrorCode.EMPTY_ROLE_MEMBER,
                "no effective role member can be adapted");
        var assignmentKeys = new HashSet<String>();
        if (effectiveMembers.stream().anyMatch(member -> !assignmentKeys.add(member.assignmentId()))) {
            throw failure(RoleCandidateErrorCode.DUPLICATE_MEMBER,
                    "duplicate assignment evidence is forbidden");
        }
        Map<String, List<RoleDirectoryMember>> grouped = effectiveMembers.stream()
                .collect(Collectors.groupingBy(RoleDirectoryMember::userId));
        if (grouped.size() > candidateLimit) {
            throw failure(RoleCandidateErrorCode.CANDIDATE_LIMIT_EXCEEDED,
                    "candidate limit exceeded; truncation is forbidden");
        }
        List<RoleCandidateUser> users = grouped.entrySet().stream()
                .map(entry -> new RoleCandidateUser(entry.getKey(),
                        CandidateSource.ROLE_DIRECTORY, entry.getValue()))
                .sorted(Comparator.comparing(RoleCandidateUser::userId)).toList();
        RoleCandidateResult draft = new RoleCandidateResult(context.roleCode(),
                context.organizationId(), directory.revision(), users, directory.resultHash(),
                RoleDirectoryResolver.CONTRACT_HASH.value(), "0".repeat(64),
                CandidateSource.ROLE_DIRECTORY, CandidateResolutionMode.ROLE_POOL_PREVIEW);
        return new RoleCandidateResult(draft.roleCode(), draft.organizationId(), draft.revision(),
                draft.candidateUsers(), draft.directoryHash(), draft.resolverContractHash(),
                RoleCandidateCanonical.hash(draft), draft.source(), draft.mode());
    }

    private RoleCandidateException failure(RoleCandidateErrorCode code, String message) {
        return new RoleCandidateException(code, message);
    }
}
