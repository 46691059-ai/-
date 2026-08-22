package cn.gov.enterprise.modules.workflow.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable workflow definition version. Published versions are never edited in place. */
public record WorkflowVersion(
        Long id,
        Long definitionId,
        int versionNo,
        Status status,
        String schemaVersion,
        String contentHash,
        String changeNote,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long publishedBy,
        LocalDateTime publishedTime,
        Long sourceVersionId,
        WorkflowEngineMode engineMode,
        WorkflowContentHashAlgorithm contentHashAlgorithm,
        int version) {

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    public WorkflowVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(engineMode, "engineMode");
        Objects.requireNonNull(contentHashAlgorithm, "contentHashAlgorithm");
        if (versionNo <= 0) throw new IllegalArgumentException("versionNo must be positive");
        if (schemaVersion == null || schemaVersion.isBlank() || schemaVersion.length() > 30) {
            throw new IllegalArgumentException("schemaVersion is invalid");
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must not precede effectiveFrom");
        }
        if (status != Status.DRAFT && (contentHash == null || publishedBy == null || publishedTime == null)) {
            throw new IllegalArgumentException("published version requires immutable publication evidence");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    /** Compatibility constructor for immutable V2.5 single-node versions. */
    public WorkflowVersion(
            Long id, Long definitionId, int versionNo, Status status, String schemaVersion,
            String contentHash, String changeNote, LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo, Long publishedBy, LocalDateTime publishedTime,
            Long sourceVersionId, int version) {
        this(id, definitionId, versionNo, status, schemaVersion, contentHash, changeNote,
                effectiveFrom, effectiveTo, publishedBy, publishedTime, sourceVersionId,
                WorkflowEngineMode.SINGLE_NODE_LEGACY,
                WorkflowContentHashAlgorithm.NODE_V1_SHA256, version);
    }

    public static WorkflowVersion draft(Long id, Long definitionId, int versionNo,
                                        String schemaVersion, String changeNote, Long sourceVersionId) {
        WorkflowEngineMode mode = "2.0".equals(schemaVersion)
                ? WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                : WorkflowEngineMode.SINGLE_NODE_LEGACY;
        WorkflowContentHashAlgorithm algorithm = mode == WorkflowEngineMode.MULTI_NODE_LINEAR_V1
                ? WorkflowContentHashAlgorithm.GRAPH_V2_SHA256
                : WorkflowContentHashAlgorithm.NODE_V1_SHA256;
        return new WorkflowVersion(id, definitionId, versionNo, Status.DRAFT, schemaVersion,
                null, changeNote, null, null, null, null, sourceVersionId, mode, algorithm, 0);
    }

    public boolean isEditable() {
        return status == Status.DRAFT;
    }

    public WorkflowVersion publish(String hash, Long publisher, LocalDateTime publishTime) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("only DRAFT workflow version can be published");
        }
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(publishTime, "publishTime");
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 value");
        }
        return new WorkflowVersion(id, definitionId, versionNo, Status.PUBLISHED, schemaVersion,
                hash, changeNote, publishTime, null, publisher, publishTime, sourceVersionId,
                engineMode, contentHashAlgorithm, version + 1);
    }

    public WorkflowVersion retire(LocalDateTime retireTime) {
        if (status != Status.PUBLISHED) {
            throw new IllegalStateException("only PUBLISHED workflow version can be retired");
        }
        Objects.requireNonNull(retireTime, "retireTime");
        return new WorkflowVersion(id, definitionId, versionNo, Status.RETIRED, schemaVersion,
                contentHash, changeNote, effectiveFrom, retireTime, publishedBy, publishedTime,
                sourceVersionId, engineMode, contentHashAlgorithm, version + 1);
    }
}
