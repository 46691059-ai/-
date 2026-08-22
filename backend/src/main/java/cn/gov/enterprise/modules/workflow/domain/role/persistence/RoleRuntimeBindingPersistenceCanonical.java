package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationCanonical;
import java.time.Instant;

/** ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1 deterministic hashing. */
public final class RoleRuntimeBindingPersistenceCanonical {
    public static final String VERSION = "ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1";

    private RoleRuntimeBindingPersistenceCanonical() { }

    public static String referenceHash(String promotionId, String activationId,
            String activationHash, String promotionHash, String promotionEvidenceHash) {
        return hash("reference", promotionId, activationId, activationHash,
                promotionHash, promotionEvidenceHash);
    }

    public static String evidenceSetHash(String activationAuditHash,
            String approvalEvidenceHash, String promotionEvidenceHash,
            String permissionEvidenceHash, String directoryContractHash) {
        return hash("evidence", activationAuditHash, approvalEvidenceHash,
                promotionEvidenceHash, permissionEvidenceHash, directoryContractHash);
    }

    public static String snapshotHash(String snapshotId,
            RoleRuntimeBindingPromotionReference reference, ResolverCode resolverCode,
            ResolverVersion resolverVersion, ResolverContractHash resolverContractHash,
            String bindingHash, String candidateHash, long directoryRevision,
            String businessScope, Instant effectiveFrom, Instant effectiveUntil,
            String evidenceSetHash) {
        return hash("snapshot", snapshotId, reference.activationId(), reference.activationHash(),
                reference.promotionId(), reference.promotionHash(),
                reference.promotionEvidenceHash(), reference.referenceHash(),
                resolverCode.value(), resolverVersion.value(), resolverContractHash.value(),
                bindingHash, candidateHash, Long.toString(directoryRevision), businessScope,
                effectiveFrom.toString(), effectiveUntil.toString(), evidenceSetHash);
    }

    public static String eventHash(String snapshotId, long sequenceNo,
            RoleRuntimeBindingLifecycleStatus fromStatus,
            RoleRuntimeBindingLifecycleStatus toStatus, String reasonCode,
            String sourceEvidenceHash, String previousEventHash,
            Instant occurredAt, String operatorId) {
        return hash("event", snapshotId, Long.toString(sequenceNo),
                fromStatus == null ? "" : fromStatus.name(), toStatus.name(), reasonCode,
                sourceEvidenceHash, previousEventHash == null ? "" : previousEventHash,
                occurredAt.toString(), operatorId);
    }

    private static String hash(String type, String... values) {
        StringBuilder canonical = new StringBuilder(VERSION).append('|').append(type);
        for (String value : values) {
            if (value == null) throw new IllegalArgumentException("canonical value must not be null");
            canonical.append('|').append(value.length()).append(':').append(value);
        }
        return PersistentActivationCanonical.sha256(canonical.toString());
    }
}
