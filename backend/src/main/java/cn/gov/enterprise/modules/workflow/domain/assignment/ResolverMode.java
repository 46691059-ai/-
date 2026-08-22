package cn.gov.enterprise.modules.workflow.domain.assignment;

/** Resolver selection mode. Only DIRECT has an executable Resolver in WF4.1. */
public enum ResolverMode {
    DIRECT,
    /** Persistence contract reserved for a future multi-candidate Resolver. */
    CANDIDATE_POOL
}
