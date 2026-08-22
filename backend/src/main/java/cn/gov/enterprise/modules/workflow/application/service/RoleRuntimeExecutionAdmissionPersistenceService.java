package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal append-only persistence boundary. It never enables ROLE runtime or creates runtime objects. */
@Service
public class RoleRuntimeExecutionAdmissionPersistenceService {
    private final RoleRuntimeExecutionAdmissionRepository admissions;
    private final RoleRuntimeExecutionAdmissionEvidenceRepository evidence;
    private final RoleRuntimeExecutionAdmissionEventRepository events;
    private final RoleRuntimeExecutionAdmissionSlotRepository slots;
    private final RoleRuntimeBindingCandidateSnapshotRepository candidates;
    private final RoleRuntimeExecutionAdmissionPersistencePolicy policy =
            new RoleRuntimeExecutionAdmissionPersistencePolicy();

    public RoleRuntimeExecutionAdmissionPersistenceService(
            RoleRuntimeExecutionAdmissionRepository admissions,
            RoleRuntimeExecutionAdmissionEvidenceRepository evidence,
            RoleRuntimeExecutionAdmissionEventRepository events,
            RoleRuntimeExecutionAdmissionSlotRepository slots,
            RoleRuntimeBindingCandidateSnapshotRepository candidates) {
        this.admissions=admissions; this.evidence=evidence; this.events=events; this.slots=slots;
        this.candidates=candidates;
    }

    @Transactional
    public PersistentRoleRuntimeExecutionAdmission persist(
            RoleRuntimeExecutionAdmissionPersistenceBundle bundle, Instant now) {
        var value = bundle.admission();
        var existing = admissions.findByRequestId(value.requestId());
        if (existing.isPresent()) {
            if (!existing.get().persistenceHash().equals(value.persistenceHash())) {
                throw new BusinessException("B26155", "idempotency payload mismatch");
            }
            return existing.get();
        }
        var byKey = admissions.findByCandidateAndIdempotencyKey(
                value.candidateSnapshotRowId(), value.idempotencyKey());
        if (byKey.isPresent()) {
            if (!byKey.get().persistenceHash().equals(value.persistenceHash())) {
                throw new BusinessException("B26155", "idempotency payload mismatch");
            }
            return byKey.get();
        }
        policy.verify(value, bundle.evidence(), now);
        candidates.lockBySnapshotId(value.snapshotId())
                .filter(candidate -> candidate.snapshotId().equals(value.snapshotId()))
                .orElseThrow(() -> new BusinessException("B26159", "source candidate missing"));
        slots.ensure(value.candidateSnapshotRowId(), value.snapshotId());
        var slot = slots.lockByCandidateSnapshotRowId(value.candidateSnapshotRowId())
                .orElseThrow(() -> new BusinessException("B26156", "admission slot missing"));
        boolean active = value.decision() == RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION;
        if (active && !slot.vacant()) throw new BusinessException("B26157", "active admission exists");
        admissions.insert(value);
        evidence.appendAll(bundle.evidence());
        if (active) {
            var approved = bundle.events().stream()
                    .filter(event -> event.eventType() == RoleRuntimeExecutionAdmissionEventType.APPROVED_FOR_EXECUTION)
                    .reduce((left, right) -> { throw new BusinessException("B26150", "duplicate final approval event"); })
                    .orElseThrow(() -> new BusinessException("B26150", "final approval event missing"));
            bundle.events().stream().filter(event -> event != approved).forEach(events::append);
            if (!slots.compareAndSetActive(value.candidateSnapshotRowId(), slot.version(), slot.activeToken(),
                    value.id(), value.admissionId(), value.decidedBy())) {
                throw new BusinessException("B26158", "admission slot concurrent update");
            }
            events.append(approved);
        } else {
            bundle.events().forEach(events::append);
        }
        return value;
    }
}
