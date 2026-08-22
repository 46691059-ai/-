package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.repository.RoleRealtimeEligibilityEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Preserves losing Prepare evidence after the Claim transaction has rolled back. */
@Service
public class RoleClaimNotCommittedEvidenceService {
    private final RoleRealtimeEligibilityEvidenceRepository repository;
    private final WorkflowIdentityGenerator ids;
    private final Clock clock;
    @Autowired
    public RoleClaimNotCommittedEvidenceService(RoleRealtimeEligibilityEvidenceRepository repository,
            WorkflowIdentityGenerator ids) { this(repository,ids,Clock.systemUTC()); }
    RoleClaimNotCommittedEvidenceService(RoleRealtimeEligibilityEvidenceRepository repository,
            WorkflowIdentityGenerator ids,Clock clock){this.repository=repository;this.ids=ids;this.clock=clock;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(RoleRealtimeEligibilityPersistenceBundle bundle,String reason,String operator) {
        var h=bundle.header();
        var persisted=repository.findByIdForUpdate(h.id()).orElse(null);
        if(persisted==null){repository.insert(bundle);persisted=h;}
        else if(!persisted.persistenceHash().equals(h.persistenceHash())) return;
        var latest=repository.findLatestEvent(h.id()).orElse(bundle.initialEvent());
        if ("CONSUMED".equals(latest.eventType()) || "CLAIM_NOT_COMMITTED".equals(latest.eventType())
                || "REJECTED".equals(latest.eventType()) || "EXPIRED".equals(latest.eventType())) return;
        Instant now=Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        var draft=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(ids.nextId(),h.id(),
                latest.sequenceNo()+1,"CLAIM_NOT_COMMITTED",null,normalize(reason),latest.eventHash(),
                "0".repeat(64),now,operator,h.claimIdempotencyKey());
        var event=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(draft.id(),draft.evidenceRowId(),
                draft.sequenceNo(),draft.eventType(),null,draft.reasonCode(),draft.previousEventHash(),
                RoleRealtimeEligibilityPersistenceCanonical.eventHash(draft),now,operator,draft.idempotencyKey());
        repository.appendEvent(event);
    }
    private static String normalize(String reason){
        String value=reason==null?"CLAIM_NOT_COMMITTED":reason.replaceAll("[^A-Za-z0-9_:-]","_");
        return value.substring(0,Math.min(value.length(),100));
    }
}
