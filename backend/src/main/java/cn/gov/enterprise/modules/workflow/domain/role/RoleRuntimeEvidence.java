package cn.gov.enterprise.modules.workflow.domain.role;

/** Minimum immutable Directory and rule evidence required by ROLE Runtime. */
public record RoleRuntimeEvidence(
        long directoryRevision,
        String directoryHash,
        String roleRuleHash,
        String candidateRuleHash,
        String sourceEvidenceHash) {

    public RoleRuntimeEvidence {
        if (directoryRevision <= 0) {
            throw new IllegalArgumentException("directoryRevision must be positive");
        }
        directoryHash = RoleCandidateResult.hash(directoryHash, "directoryHash");
        roleRuleHash = RoleCandidateResult.hash(roleRuleHash, "roleRuleHash");
        candidateRuleHash = RoleCandidateResult.hash(candidateRuleHash, "candidateRuleHash");
        sourceEvidenceHash = RoleCandidateResult.hash(sourceEvidenceHash, "sourceEvidenceHash");
    }
}
