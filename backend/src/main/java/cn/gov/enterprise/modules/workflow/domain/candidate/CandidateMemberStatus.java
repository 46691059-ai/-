package cn.gov.enterprise.modules.workflow.domain.candidate;

/** Frozen membership state; personnel changes never rewrite an INCLUDED fact. */
public enum CandidateMemberStatus {
    INCLUDED,
    CANCELLED,
    SECURITY_BLOCKED
}
