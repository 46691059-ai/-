package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CanaryGovernanceApplicationService {
    private final CanaryGovernanceRepository repository; private final WorkflowIdentityGenerator ids; private final Clock clock;
    @Autowired
    public CanaryGovernanceApplicationService(CanaryGovernanceRepository repository,WorkflowIdentityGenerator ids){this(repository,ids,Clock.systemUTC());}
    public CanaryGovernanceApplicationService(CanaryGovernanceRepository repository,WorkflowIdentityGenerator ids,Clock clock){this.repository=repository;this.ids=ids;this.clock=clock;}
    @Transactional public CanaryGovernanceRecord propose(CanaryScope scope,CanaryApprovalEvidence evidence,String reason){
        if(repository.latest(scope,Instant.now(clock)).isPresent())throw new IllegalStateException("Canary scope already governed");
        var record=CanaryGovernanceRecord.proposed(ids.nextId(),scope,evidence,reason,Instant.now(clock));repository.insert(record);return record;
    }
    @Transactional public CanaryGovernanceRecord transition(CanaryScope scope,long expectedRevision,
            CanaryGovernanceState target,String actor,String reason){
        var current=repository.latest(scope,Instant.now(clock)).orElseThrow(()->new IllegalStateException("Canary scope governance missing"));
        if(current.revision()!=expectedRevision)throw new IllegalStateException("Canary governance CAS conflict");
        var next=current.transition(ids.nextId(),target,actor,reason,Instant.now(clock));repository.insert(next);return next;
    }
}
