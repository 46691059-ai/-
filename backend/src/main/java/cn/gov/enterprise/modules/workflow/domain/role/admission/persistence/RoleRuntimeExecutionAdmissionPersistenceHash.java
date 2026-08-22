package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.util.List;

public final class RoleRuntimeExecutionAdmissionPersistenceHash {
    private RoleRuntimeExecutionAdmissionPersistenceHash() { }

    public static String capabilityRoot(List<RoleRuntimeExecutionAdmissionEvidenceRecord> records) {
        return RoleRuntimeCapabilityEvidenceRootCanonical.compute(records);
    }

    public static String compute(PersistentRoleRuntimeExecutionAdmission value) {
        String canonical = AdmissionPersistenceSupport.field("canonical", AdmissionPersistenceSupport.CANONICAL)
                + AdmissionPersistenceSupport.field("admissionId", value.admissionId())
                + AdmissionPersistenceSupport.field("requestId", value.requestId())
                + AdmissionPersistenceSupport.field("idempotencyKey", value.idempotencyKey())
                + AdmissionPersistenceSupport.field("activationHash", value.activationHash())
                + AdmissionPersistenceSupport.field("promotionHash", value.promotionHash())
                + AdmissionPersistenceSupport.field("bindingHash", value.bindingHash())
                + AdmissionPersistenceSupport.field("candidateHash", value.candidateHash())
                + AdmissionPersistenceSupport.field("resolverCode", value.resolverCode())
                + AdmissionPersistenceSupport.field("resolverVersion", value.resolverVersion())
                + AdmissionPersistenceSupport.field("resolverContractHash", value.resolverContractHash())
                + AdmissionPersistenceSupport.field("directoryRevision", value.directoryRevision())
                + AdmissionPersistenceSupport.field("directoryResultHash", value.directoryResultHash())
                + AdmissionPersistenceSupport.field("directoryFenceTokenHash", value.directoryFenceTokenHash())
                + AdmissionPersistenceSupport.field("enterpriseId", value.enterpriseId())
                + AdmissionPersistenceSupport.field("businessScope", value.businessScope())
                + AdmissionPersistenceSupport.field("definitionId", value.definitionId())
                + AdmissionPersistenceSupport.field("definitionVersionId", value.definitionVersionId())
                + AdmissionPersistenceSupport.field("nodeId", value.nodeId())
                + AdmissionPersistenceSupport.field("nodeBindingHash", value.nodeBindingHash())
                + AdmissionPersistenceSupport.field("graphHash", value.graphHash())
                + AdmissionPersistenceSupport.field("effectiveAt", value.effectiveAt())
                + AdmissionPersistenceSupport.field("featureFlagEvidenceHash", value.featureFlagEvidenceHash())
                + AdmissionPersistenceSupport.field("canaryEvidenceHash", value.canaryEvidenceHash())
                + AdmissionPersistenceSupport.field("killSwitchEvidenceHash", value.killSwitchEvidenceHash())
                + AdmissionPersistenceSupport.field("capabilityEvidenceRootHash", value.capabilityEvidenceRootHash())
                + AdmissionPersistenceSupport.field("decision", value.decision())
                + AdmissionPersistenceSupport.field("policyVersion", value.policyVersion());
        return AdmissionPersistenceSupport.sha256(canonical);
    }
}
