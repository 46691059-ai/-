package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

public final class RoleRuntimeExecutionAdmissionPersistencePolicy {
    public void verify(PersistentRoleRuntimeExecutionAdmission admission,
            List<RoleRuntimeExecutionAdmissionEvidenceRecord> evidence, Instant now) {
        if (!admission.persistenceHash().equals(RoleRuntimeExecutionAdmissionPersistenceHash.compute(admission))) {
            throw new IllegalArgumentException("persistence hash mismatch");
        }
        if (!admission.capabilityEvidenceRootHash().equals(
                RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(evidence))) {
            throw new IllegalArgumentException("capability evidence root mismatch");
        }
        if (evidence.size() != admission.executedCheckCount()
                || evidence.stream().map(RoleRuntimeExecutionAdmissionEvidenceRecord::sequenceNo)
                        .collect(java.util.stream.Collectors.toSet()).size() != evidence.size()
                || evidence.stream().anyMatch(value -> !value.admissionId().equals(admission.admissionId()))) {
            throw new IllegalArgumentException("admission evidence is incomplete or not owned");
        }
        var codes = new HashSet<String>();
        evidence.forEach(value -> { if (!codes.add(value.validatorCode())) throw new IllegalArgumentException("duplicate validator code"); });
        if (admission.decision() == cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION) {
            verifyFinalApprovalEvidence(evidence);
        }
        if (!admission.directoryFenceExpiresAt().isAfter(now) || !admission.admissionExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("admission evidence expired");
        }
    }

    private static void verifyFinalApprovalEvidence(List<RoleRuntimeExecutionAdmissionEvidenceRecord> evidence) {
        if (evidence.size() != 28) throw new IllegalArgumentException("final approval requires 28 validator evidence records");
        var bySequence = evidence.stream().collect(java.util.stream.Collectors.toMap(
                RoleRuntimeExecutionAdmissionEvidenceRecord::sequenceNo, value -> value));
        IntStream.rangeClosed(1, 28).forEach(sequence -> {
            var value = bySequence.get(sequence);
            if (value == null || !value.validatorCode().equals(
                    RoleRuntimeExecutionAdmissionValidatorContract.validatorCode(sequence))
                    || !value.result().equals("PASS")) {
                throw new IllegalArgumentException("final approval validator contract mismatch");
            }
            String capability = RoleRuntimeExecutionAdmissionValidatorContract.REQUIRED_CAPABILITIES.get(sequence);
            if (capability == null) {
                if (value.capabilityCode() != null || value.capabilityStatus() != null) {
                    throw new IllegalArgumentException("business validator cannot carry capability status");
                }
            } else if (!capability.equals(value.capabilityCode())
                    || !"READY".equals(value.capabilityStatus())) {
                throw new IllegalArgumentException("required capability is not ready");
            }
        });
    }
}
