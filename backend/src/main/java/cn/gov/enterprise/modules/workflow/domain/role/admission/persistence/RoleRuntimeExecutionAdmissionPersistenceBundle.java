package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.util.List;

public record RoleRuntimeExecutionAdmissionPersistenceBundle(
        PersistentRoleRuntimeExecutionAdmission admission,
        List<RoleRuntimeExecutionAdmissionEvidenceRecord> evidence,
        List<RoleRuntimeExecutionAdmissionEvent> events) {
    public RoleRuntimeExecutionAdmissionPersistenceBundle {
        if (admission == null || evidence == null || events == null || events.size() < 2) {
            throw new IllegalArgumentException("complete admission persistence bundle is required");
        }
        evidence = List.copyOf(evidence); events = List.copyOf(events);
        if (events.getFirst().eventType() != RoleRuntimeExecutionAdmissionEventType.ADMISSION_CREATED
                || events.stream().anyMatch(event -> !event.admissionId().equals(admission.admissionId()))) {
            throw new IllegalArgumentException("admission event chain is not owned");
        }
    }
}
