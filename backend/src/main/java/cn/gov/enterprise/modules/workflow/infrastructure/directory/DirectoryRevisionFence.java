package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import java.time.Instant;

/** Exact evidence fence. Revision-only matching is deliberately insufficient. */
public record DirectoryRevisionFence(
        String enterpriseId, String organizationId, String roleCode, Instant effectiveAt,
        long directoryRevision, String directoryResultHash, String contractHash) {

    public static DirectoryRevisionFence freeze(RoleDirectoryQuery query, RoleDirectoryResult result) {
        return new DirectoryRevisionFence(query.enterpriseId(), result.organizationId(), result.roleCode(),
                result.effectiveAt(), result.revision(), result.resultHash(), result.contractHash());
    }

    public void verify(RoleDirectoryQuery query, RoleDirectoryResult result) {
        if (!enterpriseId.equals(query.enterpriseId()) || !organizationId.equals(result.organizationId())
                || !roleCode.equals(result.roleCode()) || !effectiveAt.equals(result.effectiveAt())
                || directoryRevision != result.revision()
                || !directoryResultHash.equals(result.resultHash()) || !contractHash.equals(result.contractHash())) {
            throw new DirectoryFailure(DirectoryFailure.Code.REVISION_MISMATCH,
                    "directory revision fence does not exactly match");
        }
    }
}
