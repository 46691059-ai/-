package cn.gov.enterprise.modules.workflow.domain.canary;

/** Frozen approval evidence; runtime revalidation remains a separate concern. */
public record CanaryApprovalEvidence(String directoryRevision, int directoryCandidateCount,
        String directoryResultHash, String versionBindingHash, String manifestHash,
        String contentHash, String releaseTag, String releaseCommit,
        String structuralFingerprint) {
    public CanaryApprovalEvidence {
        if (directoryRevision == null || directoryRevision.isBlank() || directoryRevision.length() > 100
                || directoryCandidateCount <= 0 || releaseTag == null || releaseTag.isBlank()) {
            throw new IllegalArgumentException("Canary approval evidence is incomplete");
        }
        hash(directoryResultHash); hash(versionBindingHash); hash(manifestHash);
        hash(contentHash); hash(structuralFingerprint);
        if (releaseCommit == null || !releaseCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("releaseCommit must be lowercase Git SHA-1");
        }
    }
    private static void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("evidence hash must be lowercase SHA-256");
        }
    }
}
