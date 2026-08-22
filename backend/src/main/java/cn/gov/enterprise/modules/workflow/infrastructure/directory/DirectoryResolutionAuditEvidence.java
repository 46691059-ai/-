package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import java.time.Instant;

/** PII-minimized technical evidence; no phone, identity card or private address fields exist. */
public record DirectoryResolutionAuditEvidence(
        String queryHash, String providerCode, long revision, String resultHash, String contractHash,
        String providerVersion, Instant resolvedAt, int candidateCount, String outcome) { }
