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
        ResolverBindingModel resolverBindingModel,
        String resolverBindingManifestHash,
        int resolverBindingCount,
        String resolverBindingCanonicalVersion,
        int version) {

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    public WorkflowVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(engineMode, "engineMode");
        Objects.requireNonNull(contentHashAlgorithm, "contentHashAlgorithm");
        Objects.requireNonNull(resolverBindingModel, "resolverBindingModel");
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
        validateResolverBindingSnapshot(status, resolverBindingModel, resolverBindingManifestHash,
                resolverBindingCount, resolverBindingCanonicalVersion);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    /** Compatibility constructor for pre-V2.6.21 callers and Legacy USER runtime. */
    public WorkflowVersion(
            Long id, Long definitionId, int versionNo, Status status, String schemaVersion,
            String contentHash, String changeNote, LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo, Long publishedBy, LocalDateTime publishedTime,
            Long sourceVersionId, WorkflowEngineMode engineMode,
            WorkflowContentHashAlgorithm contentHashAlgorithm, int version) {
        this(id, definitionId, versionNo, status, schemaVersion, contentHash, changeNote,
                effectiveFrom, effectiveTo, publishedBy, publishedTime, sourceVersionId,
                engineMode, contentHashAlgorithm, ResolverBindingModel.LEGACY_USER_ONLY,
                null, 0, null, version);
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
                WorkflowContentHashAlgorithm.NODE_V1_SHA256,
                ResolverBindingModel.LEGACY_USER_ONLY, null, 0, null, version);
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

    public WorkflowVersion prepareResolverBindingSnapshot(
            String manifestHash, int bindingCount, String canonicalVersion) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("only DRAFT workflow version can prepare resolver bindings");
        }
        if (resolverBindingModel != ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE) {
            throw new IllegalStateException("Legacy workflow version must not prepare resolver bindings");
        }
        return new WorkflowVersion(id, definitionId, versionNo, status, schemaVersion,
                contentHash, changeNote, effectiveFrom, effectiveTo, publishedBy, publishedTime,
                sourceVersionId, engineMode, contentHashAlgorithm, resolverBindingModel,
                manifestHash, bindingCount, canonicalVersion, version + 1);
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
                engineMode, contentHashAlgorithm, resolverBindingModel,
                resolverBindingManifestHash, resolverBindingCount,
                resolverBindingCanonicalVersion, version + 1);
    }

    public WorkflowVersion retire(LocalDateTime retireTime) {
        if (status != Status.PUBLISHED) {
            throw new IllegalStateException("only PUBLISHED workflow version can be retired");
        }
        Objects.requireNonNull(retireTime, "retireTime");
        return new WorkflowVersion(id, definitionId, versionNo, Status.RETIRED, schemaVersion,
                contentHash, changeNote, effectiveFrom, retireTime, publishedBy, publishedTime,
                sourceVersionId, engineMode, contentHashAlgorithm, resolverBindingModel,
                resolverBindingManifestHash, resolverBindingCount,
                resolverBindingCanonicalVersion, version + 1);
    }

    private static void validateResolverBindingSnapshot(
            Status status, ResolverBindingModel model, String manifestHash,
            int bindingCount, String canonicalVersion) {
        if (bindingCount < 0) throw new IllegalArgumentException("resolverBindingCount must not be negative");
        if (model == ResolverBindingModel.LEGACY_USER_ONLY) {
            if (manifestHash != null || bindingCount != 0 || canonicalVersion != null) {
                throw new IllegalArgumentException("Legacy Version must not carry resolver binding evidence");
            }
            return;
        }
        boolean emptyDraft = manifestHash == null && bindingCount == 0 && canonicalVersion == null;
        boolean complete = manifestHash != null && manifestHash.matches("[0-9a-f]{64}")
                && bindingCount > 0
                && "VERSION_RESOLVER_BINDING_MANIFEST_V1".equals(canonicalVersion);
        if ((status == Status.DRAFT && !emptyDraft && !complete)
                || (status != Status.DRAFT && !complete)) {
            throw new IllegalArgumentException("Version resolver binding snapshot is incomplete");
        }
    }
}
