package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.*;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts the already evaluated 27/10 contract result into the V2.6.16 aggregate. */
@Component
public final class RoleClaimEvidenceAssembler {
    private final WorkflowIdentityGenerator ids;
    public RoleClaimEvidenceAssembler(WorkflowIdentityGenerator ids) { this.ids=ids; }

    public RoleRealtimeEligibilityPersistenceBundle assemble(RealtimeEligibilityResult result,
            RealtimeEligibilityContext context, CandidatePool pool, CandidatePoolMember member,
            String claimRequestId, String idempotencyKey) {
        var evidence=result.evidence();
        if (result.decision().status()!=RealtimeEligibilityStatus.ELIGIBLE) {
            throw new IllegalArgumentException("only ELIGIBLE result can enter ROLE Claim commit");
        }
        long evidenceRowId=ids.nextId();
        Instant checked=evidence.verifiedAt().truncatedTo(ChronoUnit.MILLIS);
        List<RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence> validators=evidence.validatorResults().stream()
                .map(v -> new RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence(ids.nextId(), evidenceRowId,
                        v.validatorCode(),v.order(),v.status().name(),v.reason(),
                        RoleRealtimeEligibilityPersistenceCanonical.validatorHash(v.order(),v.validatorCode(),
                                v.status().name(),v.reason(),v.checkedAt()),v.checkedAt()))
                .toList();
        var capabilities=new ArrayList<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence>();
        add(capabilities,evidenceRowId,"USER_STATUS","USER_STATUS",evidence.userStatusResult().status().name(),
                evidence.userStatusResult().mappingVersion(),checked,evidence.expiresAt());
        add(capabilities,evidenceRowId,"ORGANIZATION_MEMBERSHIP","ORGANIZATION_MEMBERSHIP",
                evidence.organizationMembershipResult(),checked);
        add(capabilities,evidenceRowId,"ROLE_MEMBERSHIP","ROLE_MEMBERSHIP",
                evidence.roleMembershipResult().outcome(),"ROLE_DIRECTORY_V1",checked,
                evidence.roleMembershipResult().validUntil());
        add(capabilities,evidenceRowId,"DATA_SCOPE","DATA_SCOPE",evidence.dataScopeResult(),checked);
        add(capabilities,evidenceRowId,"PLATFORM_SOD","PLATFORM_SOD",evidence.platformSoDResult(),checked);
        add(capabilities,evidenceRowId,"BUSINESS_SOD","BUSINESS_SOD",evidence.businessSoDResult(),checked);
        add(capabilities,evidenceRowId,"FEATURE_FLAG","FEATURE_FLAG",evidence.featureFlagResult(),checked);
        add(capabilities,evidenceRowId,"CANARY","CANARY",evidence.canaryResult(),checked);
        add(capabilities,evidenceRowId,"KILL_SWITCH","KILL_SWITCH",evidence.killSwitchResult(),checked);
        add(capabilities,evidenceRowId,"AUDIT","AUDIT",evidence.auditCapabilityResult(),checked);
        String validatorRoot=RoleRealtimeEligibilityPersistenceCanonical.validatorRoot(validators);
        String capabilityRoot=RoleRealtimeEligibilityPersistenceCanonical.capabilityRoot(capabilities);
        var q=context.query();
        var draft=new RoleRealtimeEligibilityPersistenceBundle.Header(evidenceRowId,"RRE-"+evidenceRowId,
                "RREQ-"+evidenceRowId,claimRequestId,idempotencyKey,1,evidence.correlationId(),
                q.workflowInstanceId(),pool.versionId(),pool.nodeId(),q.nodeExecutionId(),q.taskId(),pool.id(),
                member.id(),q.candidateUserId(),pool.bindingSetId(),pool.resolverBindingId(),
                pool.nodeResolverBindingId(),evidence.roleCode(),evidence.organizationId(),
                evidence.candidatePoolHash(),evidence.runtimeBindingHash(),evidence.eligibilityHash(),
                evidence.candidateDirectoryRevision(),evidence.claimDirectoryRevision(),
                evidence.directoryResultHash(),evidence.directoryContractHash(),
                evidence.roleMembershipResult().complete(),checked,checked,"ELIGIBLE",27,27,10,
                validatorRoot,capabilityRoot,"0".repeat(64),context.policy().canonicalVersion(),
                "CONTRACT_TEST",checked,checked,evidence.expiresAt());
        var header=withPersistenceHash(draft);
        var eventDraft=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(ids.nextId(),evidenceRowId,1,
                "PREPARED",null,"ELIGIBLE",null,"0".repeat(64),checked,
                Long.toString(q.candidateUserId()),idempotencyKey);
        var event=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(eventDraft.id(),eventDraft.evidenceRowId(),
                eventDraft.sequenceNo(),eventDraft.eventType(),null,eventDraft.reasonCode(),null,
                RoleRealtimeEligibilityPersistenceCanonical.eventHash(eventDraft),checked,eventDraft.operatorId(),idempotencyKey);
        var bundle=new RoleRealtimeEligibilityPersistenceBundle(header,validators,capabilities,event);
        RoleRealtimeEligibilityPersistenceCanonical.verify(bundle);
        return bundle;
    }

    private static RoleRealtimeEligibilityPersistenceBundle.Header withPersistenceHash(
            RoleRealtimeEligibilityPersistenceBundle.Header h) {
        String hash=RoleRealtimeEligibilityPersistenceCanonical.persistenceHash(h);
        return new RoleRealtimeEligibilityPersistenceBundle.Header(h.id(),h.evidenceId(),h.eligibilityRequestId(),
                h.claimRequestId(),h.claimIdempotencyKey(),h.attemptNo(),h.correlationId(),h.instanceId(),
                h.definitionVersionId(),h.nodeId(),h.nodeExecutionId(),h.taskId(),h.candidatePoolId(),
                h.candidateMemberId(),h.candidateUserId(),h.bindingSetId(),h.resolverBindingId(),h.nodeResolverBindingId(),
                h.roleCode(),h.organizationId(),h.candidatePoolHash(),h.runtimeBindingHash(),h.eligibilityHash(),
                h.candidateDirectoryRevision(),h.claimDirectoryRevision(),h.directoryResultHash(),h.directoryContractHash(),
                h.directoryComplete(),h.directoryEffectiveAt(),h.directoryCheckedAt(),h.decision(),
                h.terminalValidatorOrder(),h.validatorCount(),h.capabilityCount(),h.validatorRootHash(),
                h.capabilityRootHash(),hash,h.policyVersion(),h.evidenceSource(),h.claimAt(),h.verifiedAt(),h.expiresAt());
    }

    private void add(List<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence> out,long evidenceId,
            String code,String validator,RealtimeCapabilityResult value,Instant checked) {
        add(out,evidenceId,code,validator,value.outcome(),value.policyVersion(),checked,value.validUntil());
    }
    private void add(List<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence> out,long evidenceId,
            String code,String validator,RealtimeCapabilityResult.Outcome outcome,String version,
            Instant checked,Instant validUntil) {
        add(out,evidenceId,code,validator,outcome.name(),version,checked,validUntil);
    }
    private void add(List<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence> out,long evidenceId,
            String code,String validator,String outcome,String version,Instant checked,Instant validUntil) {
        String status="ACTIVE".equals(outcome)||"PASS".equals(outcome)?"PASS":"FAIL";
        String decision="PASS".equals(status)?"PASS":"DENY";
        String hash=RoleRealtimeEligibilityPersistenceCanonical.capabilityHash(code,validator,status,decision,
                version,version,checked,validUntil);
        out.add(new RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence(ids.nextId(),evidenceId,code,
                validator,status,decision,version,version,hash,checked,validUntil));
    }
}
