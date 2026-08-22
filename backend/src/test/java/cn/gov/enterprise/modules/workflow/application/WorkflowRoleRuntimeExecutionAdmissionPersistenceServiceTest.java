package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.RoleRuntimeExecutionAdmissionPersistenceService;
import cn.gov.enterprise.modules.workflow.domain.repository.*;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeExecutionAdmissionPersistenceServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-17T09:00:00Z"); private static final String H="a".repeat(64);
    private RoleRuntimeExecutionAdmissionRepository admissions; private RoleRuntimeExecutionAdmissionEvidenceRepository evidence;
    private RoleRuntimeExecutionAdmissionEventRepository events; private RoleRuntimeExecutionAdmissionSlotRepository slots;
    private RoleRuntimeBindingCandidateSnapshotRepository candidates;
    private RoleRuntimeExecutionAdmissionPersistenceService service;

    @BeforeEach void setup(){admissions=mock(RoleRuntimeExecutionAdmissionRepository.class);evidence=mock(RoleRuntimeExecutionAdmissionEvidenceRepository.class);events=mock(RoleRuntimeExecutionAdmissionEventRepository.class);slots=mock(RoleRuntimeExecutionAdmissionSlotRepository.class);candidates=mock(RoleRuntimeBindingCandidateSnapshotRepository.class);service=new RoleRuntimeExecutionAdmissionPersistenceService(admissions,evidence,events,slots,candidates);when(admissions.findByRequestId(any())).thenReturn(Optional.empty());when(admissions.findByCandidateAndIdempotencyKey(any(),any())).thenReturn(Optional.empty());var candidate=mock(cn.gov.enterprise.modules.workflow.domain.role.persistence.RoleRuntimeBindingCandidateSnapshot.class);when(candidate.snapshotId()).thenReturn("SNAP-1");when(candidates.lockBySnapshotId("SNAP-1")).thenReturn(Optional.of(candidate));when(slots.lockByCandidateSnapshotRowId(10L)).thenReturn(Optional.of(new RoleRuntimeExecutionAdmissionSlot(10L,"SNAP-1",null,null,H,"VACANT",0)));when(slots.compareAndSetActive(any(),anyInt(),any(),any(),any(),any())).thenReturn(true);}

    @Test void completeBundleMustPersistInFixedOrderAndOccupySlot(){var bundle=bundle();assertThat(service.persist(bundle,NOW)).isEqualTo(bundle.admission());var order=inOrder(slots,admissions,evidence,events);order.verify(slots).ensure(10L,"SNAP-1");order.verify(slots).lockByCandidateSnapshotRowId(10L);order.verify(admissions).insert(bundle.admission());order.verify(evidence).appendAll(bundle.evidence());order.verify(events,times(2)).append(any());order.verify(slots).compareAndSetActive(10L,0,H,1L,"ADM-1","approver");order.verify(events).append(any());}

    @Test void duplicateRequestWithSameHashMustReturnExistingWithoutWrites(){var bundle=bundle();when(admissions.findByRequestId("REQ-1")).thenReturn(Optional.of(bundle.admission()));assertThat(service.persist(bundle,NOW)).isEqualTo(bundle.admission());verifyNoInteractions(evidence,events,slots);verify(admissions,never()).insert(any());}

    @Test void evidenceFailureMustStopEventAndSlotMutation(){var bundle=bundle();doThrow(new IllegalStateException("evidence failed")).when(evidence).appendAll(any());assertThatThrownBy(()->service.persist(bundle,NOW)).isInstanceOf(IllegalStateException.class);verify(events,never()).append(any());verify(slots,never()).compareAndSetActive(any(),anyInt(),any(),any(),any(),any());}

    @Test void concurrentSlotCasMustFailClosed(){var bundle=bundle();when(slots.compareAndSetActive(any(),anyInt(),any(),any(),any(),any())).thenReturn(false);assertThatThrownBy(()->service.persist(bundle,NOW)).isInstanceOf(BusinessException.class).hasMessageContaining("concurrent");verify(events,times(2)).append(any());}

    private static RoleRuntimeExecutionAdmissionPersistenceBundle bundle(){var evidence=records();String root=RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(evidence);var draft=admission("0".repeat(64),root);var admission=admission(RoleRuntimeExecutionAdmissionPersistenceHash.compute(draft),root);var created=event(1,null,RoleRuntimeExecutionAdmissionStatus.CREATED,null,root,admission.persistenceHash());var eligible=event(2,RoleRuntimeExecutionAdmissionStatus.CREATED,RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,created.eventHash(),root,admission.persistenceHash());var approved=event(3,RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,eligible.eventHash(),root,admission.persistenceHash());return new RoleRuntimeExecutionAdmissionPersistenceBundle(admission,evidence,List.of(created,eligible,approved));}
    private static List<RoleRuntimeExecutionAdmissionEvidenceRecord> records(){return IntStream.rangeClosed(1,28).mapToObj(seq->{String capability=RoleRuntimeExecutionAdmissionValidatorContract.REQUIRED_CAPABILITIES.get(seq);return new RoleRuntimeExecutionAdmissionEvidenceRecord((long)seq,1L,"ADM-1",seq,RoleRuntimeExecutionAdmissionValidatorContract.validatorCode(seq),capability==null?"BUSINESS":"CAPABILITY","PASS",null,capability,capability==null?null:"READY",capability==null?null:"FAKE_V1","POLICY_V1","READY","ENTERPRISE","ENT-1",1L,2L,3L,NOW,H,String.format("%064x",seq),"EVIDENCE_V1");}).toList();}
    private static PersistentRoleRuntimeExecutionAdmission admission(String persistence,String root){return new PersistentRoleRuntimeExecutionAdmission(1L,"ADM-1","REQ-1","IDEM-1",10L,"SNAP-1","PROMO-1","ACT-1",H,"b".repeat(64),"c".repeat(64),"d".repeat(64),"e".repeat(64),root,persistence,"ROLE_DIRECTORY","ROLE_DIRECTORY_V1","f".repeat(64),7,"1".repeat(64),"2".repeat(64),NOW.plusSeconds(3600),NOW,"ENT-1","INVESTMENT",4L,1L,2L,3L,"3".repeat(64),"4".repeat(64),RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,"POLICY_V1",NOW,NOW.plusSeconds(1800),28,28,"5".repeat(64),"6".repeat(64),"7".repeat(64),"requester","approver",NOW);}
    private static RoleRuntimeExecutionAdmissionEvent event(long seq,RoleRuntimeExecutionAdmissionStatus from,RoleRuntimeExecutionAdmissionStatus to,String previous,String root,String persistence){return new RoleRuntimeExecutionAdmissionEvent(seq+2,1L,"ADM-1",10L,seq,seq==1?RoleRuntimeExecutionAdmissionEventType.ADMISSION_CREATED:RoleRuntimeExecutionAdmissionEventType.valueOf(to.name()),from,to,"OK",root,persistence,previous,String.valueOf(seq).repeat(64),NOW,"approver","RELEASE_APPROVER","EV-"+seq,"EVENT_V1");}
}
