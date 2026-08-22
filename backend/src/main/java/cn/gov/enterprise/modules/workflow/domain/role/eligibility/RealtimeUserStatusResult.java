package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

public record RealtimeUserStatusResult(
        RealtimeUserStatus status,
        String mappingVersion,
        String evidenceHash) {
    public RealtimeUserStatusResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        mappingVersion = RealtimeEligibilityQuery.text(mappingVersion, "mappingVersion", 100);
        evidenceHash = RealtimeEligibilityQuery.hash(evidenceHash, "evidenceHash");
    }
}
