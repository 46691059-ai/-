package cn.gov.enterprise.modules.workflow.domain.assignment;

/** Runtime eligibility of a statically registered resolver version. */
public enum ResolverStatus {
    /** Contract is registered for validation but the resolver is not executable. */
    PREPARED,
    ACTIVE,
    DEPRECATED,
    RETIRED
}
