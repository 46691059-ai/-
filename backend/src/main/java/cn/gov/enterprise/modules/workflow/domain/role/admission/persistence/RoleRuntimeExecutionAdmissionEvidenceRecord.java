package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.time.Instant;
import java.util.Objects;

public record RoleRuntimeExecutionAdmissionEvidenceRecord(
        Long id, Long admissionRowId, String admissionId, int sequenceNo, String validatorCode,
        String evidenceType, String result, String blockReason, String capabilityCode,
        String capabilityStatus, String providerVersion, String policyVersion,
        String observedValueCode, String scopeType, String scopeEnterpriseId,
        Long scopeDefinitionId, Long scopeDefinitionVersionId, Long scopeNodeId,
        Instant checkedAt, String subjectHash, String evidenceHash, String canonicalVersion) {

    public RoleRuntimeExecutionAdmissionEvidenceRecord {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(admissionRowId, "admissionRowId");
        admissionId = AdmissionPersistenceSupport.text(admissionId, "admissionId", 100);
        validatorCode = AdmissionPersistenceSupport.text(validatorCode, "validatorCode", 100);
        evidenceType = AdmissionPersistenceSupport.text(evidenceType, "evidenceType", 32);
        result = AdmissionPersistenceSupport.text(result, "result", 16);
        policyVersion = AdmissionPersistenceSupport.text(policyVersion, "policyVersion", 64);
        canonicalVersion = AdmissionPersistenceSupport.text(canonicalVersion, "canonicalVersion", 64);
        AdmissionPersistenceSupport.hash(subjectHash, "subjectHash");
        AdmissionPersistenceSupport.hash(evidenceHash, "evidenceHash");
        Objects.requireNonNull(checkedAt, "checkedAt");
        if (sequenceNo < 1 || sequenceNo > 28 || !(result.equals("PASS") || result.equals("FAIL"))) {
            throw new IllegalArgumentException("invalid admission evidence result or sequence");
        }
        if (result.equals("PASS") != (blockReason == null)) {
            throw new IllegalArgumentException("blockReason must exist only for failed evidence");
        }
    }
}
