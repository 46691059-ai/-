package cn.gov.enterprise.modules.workflow.domain.role;

/** Fail-closed errors for ROLE_CANDIDATE_CANONICAL_V1 adaptation. */
public enum RoleCandidateErrorCode {
    EMPTY_ROLE_MEMBER,
    DUPLICATE_MEMBER,
    CANDIDATE_LIMIT_EXCEEDED,
    ROLE_RESULT_HASH_INVALID,
    ROLE_DIRECTORY_CONTRACT_INVALID,
    ROLE_DIRECTORY_REVISION_INVALID
}
