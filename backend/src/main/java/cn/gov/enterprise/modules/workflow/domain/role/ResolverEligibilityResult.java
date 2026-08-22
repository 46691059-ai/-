package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverDescriptor;

/** Metadata-only registry check result; it never exposes an executable resolver. */
public record ResolverEligibilityResult(
        RoleRuntimeEligibility.Status status,
        AssignmentResolverDescriptor descriptor, String reason) {
    public ResolverEligibilityResult {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        reason = reason == null ? "" : reason.trim();
    }

    public boolean ready() { return status == RoleRuntimeEligibility.Status.READY; }
}
