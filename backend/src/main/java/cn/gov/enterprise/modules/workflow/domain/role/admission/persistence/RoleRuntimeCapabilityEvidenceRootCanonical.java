package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Database-verifiable canonical root for the frozen eight required capabilities. */
public final class RoleRuntimeCapabilityEvidenceRootCanonical {
    public static final String VERSION = "ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1";

    private RoleRuntimeCapabilityEvidenceRootCanonical() { }

    public static String compute(List<RoleRuntimeExecutionAdmissionEvidenceRecord> records) {
        if (records == null) throw new IllegalArgumentException("evidence is required");
        List<RoleRuntimeExecutionAdmissionEvidenceRecord> capabilities = records.stream()
                .filter(value -> value.capabilityCode() != null)
                .sorted(Comparator.comparing(RoleRuntimeExecutionAdmissionEvidenceRecord::capabilityCode))
                .toList();
        var seen = new HashSet<String>();
        StringBuilder canonical = new StringBuilder(AdmissionPersistenceSupport.field("canonical", VERSION));
        for (var value : capabilities) {
            if (!seen.add(value.capabilityCode())) {
                throw new IllegalArgumentException("duplicate capability code");
            }
            if (value.providerVersion() == null || value.providerVersion().isBlank()) {
                throw new IllegalArgumentException("capability provider version is required");
            }
            canonical.append(AdmissionPersistenceSupport.field("sequence", value.sequenceNo()))
                    .append(AdmissionPersistenceSupport.field("validatorCode", value.validatorCode()))
                    .append(AdmissionPersistenceSupport.field("capabilityCode", value.capabilityCode()))
                    .append(AdmissionPersistenceSupport.field("capabilityStatus", value.capabilityStatus()))
                    .append(AdmissionPersistenceSupport.field("result", value.result()))
                    .append(AdmissionPersistenceSupport.field("evidenceHash", value.evidenceHash()))
                    .append(AdmissionPersistenceSupport.field("providerVersion", value.providerVersion()))
                    .append(AdmissionPersistenceSupport.field("policyVersion", value.policyVersion()));
        }
        return AdmissionPersistenceSupport.sha256(canonical.toString());
    }
}
