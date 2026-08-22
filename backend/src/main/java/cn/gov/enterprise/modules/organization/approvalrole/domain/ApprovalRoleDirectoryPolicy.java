package cn.gov.enterprise.modules.organization.approvalrole.domain;

import java.time.Instant;
import java.util.List;

public final class ApprovalRoleDirectoryPolicy {
    public List<ApprovalRoleAssignment> effective(List<ApprovalRoleAssignment> assignments, Instant at) {
        return assignments.stream().filter(a -> a.effectiveAt(at)).toList();
    }

    public void rejectSourceConflict(List<ApprovalRoleAssignment> assignments) {
        for (int i = 0; i < assignments.size(); i++) {
            for (int j = i + 1; j < assignments.size(); j++) {
                ApprovalRoleAssignment left = assignments.get(i), right = assignments.get(j);
                boolean sameEvidence = left.source().sourceSystem().equals(right.source().sourceSystem())
                        && left.source().sourceReference().equals(right.source().sourceReference());
                if (sameEvidence && (left.userId() != right.userId()
                        || !left.source().sourceReferenceHash().equals(right.source().sourceReferenceHash()))) {
                    throw new ApprovalRoleDirectoryFailure(ApprovalRoleDirectoryFailureCode.DIRECTORY_SOURCE_CONFLICT,
                            "one source reference asserts conflicting role membership");
                }
            }
        }
    }
}
