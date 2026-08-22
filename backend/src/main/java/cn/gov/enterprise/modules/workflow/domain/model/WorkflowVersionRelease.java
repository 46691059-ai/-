package cn.gov.enterprise.modules.workflow.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable audit fact for one atomic current-version switch. */
public record WorkflowVersionRelease(
        Long id, Long definitionId, Long previousVersionId, Long publishedVersionId,
        int publishedVersionNo, String contentHash, Long operatorUserId, Long operatorOrgId,
        LocalDateTime publishedTime, String traceId, String validationSummary,
        WorkflowEngineMode engineMode, WorkflowContentHashAlgorithm contentHashAlgorithm) {

    public WorkflowVersionRelease {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(publishedVersionId, "publishedVersionId");
        if (publishedVersionNo <= 0) throw new IllegalArgumentException("publishedVersionNo must be positive");
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 value");
        }
        Objects.requireNonNull(operatorUserId, "operatorUserId");
        Objects.requireNonNull(operatorOrgId, "operatorOrgId");
        Objects.requireNonNull(publishedTime, "publishedTime");
        Objects.requireNonNull(engineMode, "engineMode");
        Objects.requireNonNull(contentHashAlgorithm, "contentHashAlgorithm");
        if (traceId != null && traceId.length() > 64) throw new IllegalArgumentException("traceId is too long");
        if (validationSummary != null && validationSummary.length() > 1000) {
            throw new IllegalArgumentException("validationSummary is too long");
        }
    }

    public WorkflowVersionRelease(
            Long id, Long definitionId, Long previousVersionId, Long publishedVersionId,
            int publishedVersionNo, String contentHash, Long operatorUserId, Long operatorOrgId,
            LocalDateTime publishedTime, String traceId, String validationSummary) {
        this(id, definitionId, previousVersionId, publishedVersionId, publishedVersionNo,
                contentHash, operatorUserId, operatorOrgId, publishedTime, traceId,
                validationSummary, WorkflowEngineMode.SINGLE_NODE_LEGACY,
                WorkflowContentHashAlgorithm.NODE_V1_SHA256);
    }
}
