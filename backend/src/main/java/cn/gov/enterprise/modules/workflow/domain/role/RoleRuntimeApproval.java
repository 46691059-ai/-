package cn.gov.enterprise.modules.workflow.domain.role;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.time.Instant;
import java.util.Objects;

/** Immutable view of one ROLE Runtime Binding governance decision. */
public record RoleRuntimeApproval(
        Long id,
        String proposalHash,
        String eligibilityHash,
        ResolverCode resolverCode,
        ResolverVersion resolverVersion,
        ResolverContractHash contractHash,
        Status status,
        String approvedBy,
        Instant approvedAt,
        String rejectReason,
        String auditInfo,
        int version) {

    public enum Status { PENDING, APPROVED, REJECTED, EXPIRED }

    public RoleRuntimeApproval {
        positive(id, "id");
        proposalHash = RoleCandidateResult.hash(proposalHash, "proposalHash");
        eligibilityHash = RoleCandidateResult.hash(eligibilityHash, "eligibilityHash");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(contractHash, "contractHash");
        Objects.requireNonNull(status, "status");
        auditInfo = RoleDirectoryQuery.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        validateDecision(status, approvedBy, approvedAt, rejectReason);
    }

    public static RoleRuntimeApproval pending(
            Long id, String proposalHash, String eligibilityHash,
            ResolverCode resolverCode, ResolverVersion resolverVersion,
            ResolverContractHash contractHash, String auditInfo) {
        return new RoleRuntimeApproval(id, proposalHash, eligibilityHash, resolverCode,
                resolverVersion, contractHash, Status.PENDING, null, null, null,
                auditInfo, 0);
    }

    public RoleRuntimeApproval approve(String operator, Instant decisionTime) {
        requirePending();
        return new RoleRuntimeApproval(id, proposalHash, eligibilityHash, resolverCode,
                resolverVersion, contractHash, Status.APPROVED,
                RoleDirectoryQuery.required(operator, "approvedBy", 64),
                Objects.requireNonNull(decisionTime, "decisionTime"), null, auditInfo, version);
    }

    public RoleRuntimeApproval reject(String reason) {
        requirePending();
        return new RoleRuntimeApproval(id, proposalHash, eligibilityHash, resolverCode,
                resolverVersion, contractHash, Status.REJECTED, null, null,
                RoleDirectoryQuery.required(reason, "rejectReason", 500), auditInfo, version);
    }

    public RoleRuntimeApproval expire() {
        if (status != Status.PENDING && status != Status.APPROVED) {
            throw new IllegalStateException("only PENDING or APPROVED approval can expire");
        }
        return new RoleRuntimeApproval(id, proposalHash, eligibilityHash, resolverCode,
                resolverVersion, contractHash, Status.EXPIRED, null, null, null,
                auditInfo, version);
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("only PENDING approval can be decided");
        }
    }

    private static void validateDecision(
            Status status, String approvedBy, Instant approvedAt, String rejectReason) {
        switch (status) {
            case PENDING, EXPIRED -> {
                if (approvedBy != null || approvedAt != null || rejectReason != null) {
                    throw new IllegalArgumentException(status + " must not contain decision fields");
                }
            }
            case APPROVED -> {
                RoleDirectoryQuery.required(approvedBy, "approvedBy", 64);
                Objects.requireNonNull(approvedAt, "approvedAt");
                if (rejectReason != null) {
                    throw new IllegalArgumentException("APPROVED must not contain rejectReason");
                }
            }
            case REJECTED -> {
                if (approvedBy != null || approvedAt != null) {
                    throw new IllegalArgumentException("REJECTED must not contain approval fields");
                }
                RoleDirectoryQuery.required(rejectReason, "rejectReason", 500);
            }
        }
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
