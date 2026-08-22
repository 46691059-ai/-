package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RoleRuntimeExecutionAdmissionEvent(
        Long id, Long admissionRowId, String admissionId, Long candidateSnapshotRowId,
        long sequenceNo, RoleRuntimeExecutionAdmissionEventType eventType,
        RoleRuntimeExecutionAdmissionStatus fromStatus, RoleRuntimeExecutionAdmissionStatus toStatus,
        String reasonCode, String sourceEvidenceRootHash, String sourcePersistenceHash, String previousEventHash,
        String eventHash, Instant occurredAt, String operatorId, String operatorRole,
        String idempotencyKey, String canonicalVersion) {

    private static final Map<RoleRuntimeExecutionAdmissionStatus, Set<RoleRuntimeExecutionAdmissionStatus>> NEXT = Map.of(
            RoleRuntimeExecutionAdmissionStatus.CREATED, Set.of(RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,
                    RoleRuntimeExecutionAdmissionStatus.BLOCKED, RoleRuntimeExecutionAdmissionStatus.REJECTED),
            RoleRuntimeExecutionAdmissionStatus.ELIGIBLE, Set.of(RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,
                    RoleRuntimeExecutionAdmissionStatus.BLOCKED, RoleRuntimeExecutionAdmissionStatus.REJECTED,
                    RoleRuntimeExecutionAdmissionStatus.EXPIRED),
            RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION, Set.of(
                    RoleRuntimeExecutionAdmissionStatus.REVOKED, RoleRuntimeExecutionAdmissionStatus.EXPIRED));

    public RoleRuntimeExecutionAdmissionEvent {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(admissionRowId, "admissionRowId");
        Objects.requireNonNull(candidateSnapshotRowId, "candidateSnapshotRowId");
        admissionId = AdmissionPersistenceSupport.text(admissionId, "admissionId", 100);
        reasonCode = AdmissionPersistenceSupport.text(reasonCode, "reasonCode", 100);
        operatorId = AdmissionPersistenceSupport.text(operatorId, "operatorId", 100);
        operatorRole = AdmissionPersistenceSupport.text(operatorRole, "operatorRole", 32);
        idempotencyKey = AdmissionPersistenceSupport.text(idempotencyKey, "idempotencyKey", 100);
        canonicalVersion = AdmissionPersistenceSupport.text(canonicalVersion, "canonicalVersion", 64);
        AdmissionPersistenceSupport.hash(sourceEvidenceRootHash, "sourceEvidenceRootHash");
        AdmissionPersistenceSupport.hash(sourcePersistenceHash, "sourcePersistenceHash");
        AdmissionPersistenceSupport.hash(eventHash, "eventHash");
        if (previousEventHash != null) AdmissionPersistenceSupport.hash(previousEventHash, "previousEventHash");
        Objects.requireNonNull(eventType, "eventType"); Objects.requireNonNull(toStatus, "toStatus");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be positive");
        if (sequenceNo == 1) {
            if (eventType != RoleRuntimeExecutionAdmissionEventType.ADMISSION_CREATED || fromStatus != null
                    || toStatus != RoleRuntimeExecutionAdmissionStatus.CREATED || previousEventHash != null) {
                throw new IllegalArgumentException("first event must create the admission");
            }
        } else if (fromStatus == null || !NEXT.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
            throw new IllegalArgumentException("illegal admission event transition");
        }
    }
}
