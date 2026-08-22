package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorkflowRoleRuntimeExecutionAdmissionPersistenceDomainTest {
    private static final Instant NOW=Instant.parse("2026-08-17T09:00:00Z");
    private static final String H="a".repeat(64);

    @Test void capabilityRootMustBeStableAcrossOrderAndSensitiveToContent(){
        var first=evidence(21,"DIRECTORY_READY","DIRECTORY",H); var second=evidence(22,"REALTIME_ELIGIBILITY_READY","REALTIME_ELIGIBILITY","b".repeat(64));
        assertThat(RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(List.of(first,second)))
                .isEqualTo(RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(List.of(second,first)));
        assertThat(RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(List.of(first,second)))
                .isNotEqualTo(RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(List.of(first,evidence(22,"REALTIME_ELIGIBILITY_READY","REALTIME_ELIGIBILITY","c".repeat(64)))));
    }

    @Test void persistenceHashMustBeStableAndSeparatedFromExecutionHash(){
        var evidence=List.of(evidence(21,"DIRECTORY_READY","DIRECTORY",H)); var draft=admission("0".repeat(64),RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(evidence));
        String hash=RoleRuntimeExecutionAdmissionPersistenceHash.compute(draft); var frozen=admission(hash,draft.capabilityEvidenceRootHash());
        assertThat(RoleRuntimeExecutionAdmissionPersistenceHash.compute(frozen)).isEqualTo(hash).isNotEqualTo(frozen.executionAdmissionHash());
    }

    @Test void finalApprovalPolicyMustRequireExactContractAndReadyCapabilities(){
        var complete=completeEvidence("READY");
        var valid=hashedAdmission(complete);
        new RoleRuntimeExecutionAdmissionPersistencePolicy().verify(valid,complete,NOW);

        var incomplete=complete.subList(0,27);
        assertThatThrownBy(()->new RoleRuntimeExecutionAdmissionPersistencePolicy()
                .verify(hashedAdmission(incomplete),incomplete,NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("28 validator");

        var notReady=completeEvidence("NOT_READY");
        assertThatThrownBy(()->new RoleRuntimeExecutionAdmissionPersistencePolicy()
                .verify(hashedAdmission(notReady),notReady,NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not ready");
    }

    @Test void revokedAndExpiredEventsMustBeTerminal(){
        var created=event(1,null,RoleRuntimeExecutionAdmissionStatus.CREATED,null);
        var eligible=event(2,RoleRuntimeExecutionAdmissionStatus.CREATED,RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,created.eventHash());
        var approved=event(3,RoleRuntimeExecutionAdmissionStatus.ELIGIBLE,RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,eligible.eventHash());
        var revoked=event(4,RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,RoleRuntimeExecutionAdmissionStatus.REVOKED,approved.eventHash());
        assertThat(revoked.toStatus()).isEqualTo(RoleRuntimeExecutionAdmissionStatus.REVOKED);
        assertThatThrownBy(()->new RoleRuntimeExecutionAdmissionEvent(5L,1L,"ADM-1",10L,5,
                RoleRuntimeExecutionAdmissionEventType.APPROVED_FOR_EXECUTION,RoleRuntimeExecutionAdmissionStatus.REVOKED,
                RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,"ILLEGAL",H,H,revoked.eventHash(),H,NOW,"user","RELEASE_APPROVER","E-4","V1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void admissionPersistenceDomainMustRemainFrameworkFree() throws Exception {
        Path root=Path.of("src/main/java/cn/gov/enterprise/modules/workflow/domain/role/admission/persistence");
        List<String> violations=new ArrayList<>(); try(var files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
            String source=Files.readString(file); for(String token:List.of("import org.springframework.","import com.baomidou.","infrastructure.persistence","Entity")) if(source.contains(token)) violations.add(file+" -> "+token);
        }} assertThat(violations).isEmpty();
    }

    static RoleRuntimeExecutionAdmissionEvidenceRecord evidence(int seq,String code,String capability,String hash){return new RoleRuntimeExecutionAdmissionEvidenceRecord((long)seq,1L,"ADM-1",seq,code,"CAPABILITY","PASS",null,capability,"READY","FAKE_V1","POLICY_V1","READY","ENTERPRISE","ENT-1",1L,2L,3L,NOW,H,hash,"EVIDENCE_V1");}
    static PersistentRoleRuntimeExecutionAdmission admission(String persistenceHash,String root){return new PersistentRoleRuntimeExecutionAdmission(1L,"ADM-1","REQ-1","IDEM-1",10L,"SNAP-1","PROMO-1","ACT-1",H,"b".repeat(64),"c".repeat(64),"d".repeat(64),"e".repeat(64),root,persistenceHash,"ROLE_DIRECTORY","ROLE_DIRECTORY_V1","f".repeat(64),7,"1".repeat(64),"2".repeat(64),NOW.plusSeconds(3600),NOW,"ENT-1","INVESTMENT",4L,1L,2L,3L,"3".repeat(64),"4".repeat(64),RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,"POLICY_V1",NOW,NOW.plusSeconds(1800),1,1,"5".repeat(64),"6".repeat(64),"7".repeat(64),"requester","approver",NOW);}
    static RoleRuntimeExecutionAdmissionEvent event(long seq,RoleRuntimeExecutionAdmissionStatus from,RoleRuntimeExecutionAdmissionStatus to,String previous){return new RoleRuntimeExecutionAdmissionEvent(seq,1L,"ADM-1",10L,seq,seq==1?RoleRuntimeExecutionAdmissionEventType.ADMISSION_CREATED:RoleRuntimeExecutionAdmissionEventType.valueOf(to.name()),from,to,"OK",H,H,previous,String.valueOf(seq).repeat(64),NOW,"user","RELEASE_APPROVER","E-"+seq,"V1");}

    private static List<RoleRuntimeExecutionAdmissionEvidenceRecord> completeEvidence(String capabilityStatus){return IntStream.rangeClosed(1,28).mapToObj(sequence->{String capability=RoleRuntimeExecutionAdmissionValidatorContract.REQUIRED_CAPABILITIES.get(sequence);return new RoleRuntimeExecutionAdmissionEvidenceRecord((long)sequence,1L,"ADM-1",sequence,RoleRuntimeExecutionAdmissionValidatorContract.validatorCode(sequence),capability==null?"BUSINESS":"CAPABILITY","PASS",null,capability,capability==null?null:capabilityStatus,capability==null?null:"FAKE_V1","POLICY_V1","READY","ENTERPRISE","ENT-1",1L,2L,3L,NOW,H,String.format("%064x",sequence),"EVIDENCE_V1");}).toList();}
    private static PersistentRoleRuntimeExecutionAdmission hashedAdmission(List<RoleRuntimeExecutionAdmissionEvidenceRecord> evidence){String root=RoleRuntimeExecutionAdmissionPersistenceHash.capabilityRoot(evidence);int count=evidence.size();var draft=new PersistentRoleRuntimeExecutionAdmission(1L,"ADM-1","REQ-1","IDEM-1",10L,"SNAP-1","PROMO-1","ACT-1",H,"b".repeat(64),"c".repeat(64),"d".repeat(64),"e".repeat(64),root,"0".repeat(64),"ROLE_DIRECTORY","ROLE_DIRECTORY_V1","f".repeat(64),7,"1".repeat(64),"2".repeat(64),NOW.plusSeconds(3600),NOW,"ENT-1","INVESTMENT",4L,1L,2L,3L,"3".repeat(64),"4".repeat(64),RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION,"POLICY_V1",NOW,NOW.plusSeconds(1800),count,count,"5".repeat(64),"6".repeat(64),"7".repeat(64),"requester","approver",NOW);return new PersistentRoleRuntimeExecutionAdmission(draft.id(),draft.admissionId(),draft.requestId(),draft.idempotencyKey(),draft.candidateSnapshotRowId(),draft.snapshotId(),draft.promotionId(),draft.activationId(),draft.activationHash(),draft.promotionHash(),draft.bindingHash(),draft.candidateHash(),draft.executionAdmissionHash(),draft.capabilityEvidenceRootHash(),RoleRuntimeExecutionAdmissionPersistenceHash.compute(draft),draft.resolverCode(),draft.resolverVersion(),draft.resolverContractHash(),draft.directoryRevision(),draft.directoryResultHash(),draft.directoryFenceTokenHash(),draft.directoryFenceExpiresAt(),draft.directoryVerifiedAt(),draft.enterpriseId(),draft.businessScope(),draft.definitionReleaseId(),draft.definitionId(),draft.definitionVersionId(),draft.nodeId(),draft.nodeBindingHash(),draft.graphHash(),draft.decision(),draft.policyVersion(),draft.effectiveAt(),draft.admissionExpiresAt(),draft.executedCheckCount(),draft.lastCheckSequence(),draft.featureFlagEvidenceHash(),draft.canaryEvidenceHash(),draft.killSwitchEvidenceHash(),draft.requestedBy(),draft.decidedBy(),draft.decidedAt());}
}
