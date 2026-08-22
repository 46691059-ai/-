package cn.gov.enterprise.modules.workflow.domain.candidate;

/** Candidate Pool lifecycle. WF4.1 can create only CREATED and AVAILABLE. */
public enum CandidatePoolStatus {
    CREATED,
    AVAILABLE,
    CLAIMED,
    EXPIRED,
    CANCELLED,
    CLOSED
}
