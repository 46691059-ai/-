package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.RoleRealtimeEligibilityEvidenceTransactionService;
import cn.gov.enterprise.modules.workflow.domain.RoleRealtimeEligibilityPersistenceCanonicalTest;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRealtimeEligibilityEvidenceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoleRealtimeEligibilityEvidenceTransactionServiceTest {
    @Test void writesAggregateOnceAndReturnsSameEvidenceForSameIdempotentPayload(){
        var repo=mock(RoleRealtimeEligibilityEvidenceRepository.class);var service=new RoleRealtimeEligibilityEvidenceTransactionService(repo);
        var bundle=RoleRealtimeEligibilityPersistenceCanonicalTest.bundle();
        when(repo.findByRequestId("REQ-1")).thenReturn(Optional.empty(),Optional.of(bundle.header()));
        when(repo.findByClaimAttempt(5,8,"CLAIM-REQ-1",1)).thenReturn(Optional.empty());
        assertThat(service.persist(bundle)).isEqualTo(bundle.header()); assertThat(service.persist(bundle)).isEqualTo(bundle.header());
        verify(repo,times(1)).insert(bundle);
    }
    @Test void rejectsIdempotencyPayloadDrift(){
        var repo=mock(RoleRealtimeEligibilityEvidenceRepository.class);var service=new RoleRealtimeEligibilityEvidenceTransactionService(repo);
        var bundle=RoleRealtimeEligibilityPersistenceCanonicalTest.bundle(); var h=bundle.header();
        var changed=new cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.RoleRealtimeEligibilityPersistenceBundle.Header(
                h.id(),h.evidenceId(),h.eligibilityRequestId(),h.claimRequestId(),h.claimIdempotencyKey(),h.attemptNo(),h.correlationId(),h.instanceId(),h.definitionVersionId(),h.nodeId(),h.nodeExecutionId(),h.taskId(),h.candidatePoolId(),h.candidateMemberId(),h.candidateUserId(),h.bindingSetId(),h.resolverBindingId(),h.nodeResolverBindingId(),h.roleCode(),h.organizationId(),h.candidatePoolHash(),h.runtimeBindingHash(),h.eligibilityHash(),h.candidateDirectoryRevision(),h.claimDirectoryRevision(),h.directoryResultHash(),h.directoryContractHash(),h.directoryComplete(),h.directoryEffectiveAt(),h.directoryCheckedAt(),h.decision(),h.terminalValidatorOrder(),h.validatorCount(),h.capabilityCount(),h.validatorRootHash(),h.capabilityRootHash(),"b".repeat(64),h.policyVersion(),h.evidenceSource(),h.claimAt(),h.verifiedAt(),h.expiresAt());
        when(repo.findByRequestId("REQ-1")).thenReturn(Optional.of(changed));
        assertThatThrownBy(()->service.persist(bundle)).isInstanceOf(BusinessException.class);
    }
}
