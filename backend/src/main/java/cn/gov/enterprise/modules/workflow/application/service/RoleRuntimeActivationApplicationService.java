package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationCommand;
import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationResult;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryGovernanceState;
import cn.gov.enterprise.modules.workflow.domain.repository.ActivationEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationDecisionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationRequestRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeActivationEventRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationEvent;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeActivationState;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fail-closed transaction for the independent ROLE Runtime activation event. */
@Service
public class RoleRuntimeActivationApplicationService {
    public static final String DESIGN_COMMIT="a9a1f3d3962b9ad4a0f4e4bb9d81b617fc820175";
    public static final String OBSERVATION_COMMIT="82945b58e751a3922db87ac721c5e946b49a8f84";
    public static final String ENABLEMENT_EVIDENCE_COMMIT="fd833003e6134b7c0e39a08d513fd0aa2af195eb";
    public static final String RELEASE_COMMIT="c5946d272e8eb88115671d66b46e8c8ec67b1477";
    public static final String RELEASE_TAG="workflow-v1.0.0-rc2.1";
    public static final String DIRECTORY_HASH="2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234";
    public static final String VERSION_HASH="5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c";
    public static final String MANIFEST_HASH="e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed";
    public static final String CONTENT_HASH="b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade";
    public static final String STRUCTURAL_HASH="20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9";
    private static final Set<String> REQUIRED_APPROVERS=Set.of("BUSINESS_OWNER","SECURITY_AUDIT","RELEASE_APPROVER");

    private final RoleRuntimeActivationEventRepository events;
    private final PersistentActivationRequestRepository requests;
    private final PersistentActivationDecisionRepository decisions;
    private final ActivationEvidenceRepository evidence;
    private final CanaryGovernanceRepository canary;
    private final WorkflowIdentityGenerator ids;
    private final String killSwitch;
    private final Clock clock;

    @Autowired
    public RoleRuntimeActivationApplicationService(RoleRuntimeActivationEventRepository events,
            PersistentActivationRequestRepository requests, PersistentActivationDecisionRepository decisions,
            ActivationEvidenceRepository evidence, CanaryGovernanceRepository canary,
            WorkflowIdentityGenerator ids,
            @Value("${workflow.role-runtime.kill-switch:STOP_NEW_AND_CLAIM}") String killSwitch) {
        this(events,requests,decisions,evidence,canary,ids,killSwitch,Clock.systemUTC());
    }
    public RoleRuntimeActivationApplicationService(RoleRuntimeActivationEventRepository events,
            PersistentActivationRequestRepository requests, PersistentActivationDecisionRepository decisions,
            ActivationEvidenceRepository evidence, CanaryGovernanceRepository canary,
            WorkflowIdentityGenerator ids,String killSwitch,Clock clock) {
        this.events=events;this.requests=requests;this.decisions=decisions;this.evidence=evidence;
        this.canary=canary;this.ids=ids;this.killSwitch=killSwitch;this.clock=clock;
    }

    @Transactional
    public RoleRuntimeActivationResult activate(RoleRuntimeActivationCommand command) {
        validateFrozenBindings(command);
        var request=requests.findByActivationId(command.authorizationId())
                .orElseThrow(()->stale("authorization identity missing"));
        if(request.status()!=PersistentActivationStatus.PERSISTED
                || !request.approvalEvidenceHash().equals(command.approvalEvidenceHash())) throw stale("authorization evidence mismatch");
        var approved=decisions.findByActivationId(command.authorizationId()).stream()
                .filter(d->"APPROVE".equals(d.decision())).map(d->d.approverType()).collect(java.util.stream.Collectors.toSet());
        if(!approved.containsAll(REQUIRED_APPROVERS) || evidence.findByActivationId(command.authorizationId()).isEmpty()) throw stale("authorization approval trail incomplete");
        var now=Instant.now(clock);
        var current=canary.latest(command.scope(),now).orElseThrow(()->stale("Canary scope missing"));
        if(current.state()!=CanaryGovernanceState.ENABLED || current.revision()!=3
                || canary.countByExactScopeAndState(command.scope(),CanaryGovernanceState.ENABLED)!=1
                || !current.evidence().releaseCommit().equals(command.runtimeReleaseCommit())
                || !current.evidence().releaseTag().equals(command.runtimeReleaseTag())
                || !current.evidence().directoryResultHash().equals(command.directoryResultHash())
                || !current.evidence().versionBindingHash().equals(command.versionBindingHash())
                || !current.evidence().manifestHash().equals(command.manifestHash())
                || !current.evidence().contentHash().equals(command.contentHash())
                || !current.evidence().structuralFingerprint().equals(command.structuralFingerprint())) throw stale("Canary state or evidence drift");
        if(!"STOP_NEW_AND_CLAIM".equals(killSwitch)) throw stale("Kill Switch drift");
        var existing=events.findByExactScope(command.scope());
        if(existing.isPresent()) return classifyReplay(existing.orElseThrow(),command);
        var event=event(command,now);
        if(events.append(event)) return RoleRuntimeActivationResult.ACTIVATED;
        return classifyReplay(events.findByExactScope(command.scope())
                .orElseThrow(()->new IllegalStateException("activation duplicate without authoritative event")),command);
    }

    private RoleRuntimeActivationEvent event(RoleRuntimeActivationCommand c,Instant now){
        return new RoleRuntimeActivationEvent(ids.nextId(),c.eventId(),c.scope(),RoleRuntimeActivationEvent.EVENT_TYPE,1,1,
                RoleRuntimeActivationState.DISABLED,RoleRuntimeActivationState.ACTIVATED,c.authorizationId(),c.authorizationType(),
                c.authorizationCommit(),c.observationEvidenceCommit(),c.runtimeEnablementEvidenceCommit(),c.runtimeReleaseCommit(),
                c.runtimeReleaseTag(),c.directoryResultHash(),c.versionBindingHash(),c.manifestHash(),c.contentHash(),
                c.structuralFingerprint(),c.actorType(),c.actorId(),now);
    }
    private RoleRuntimeActivationResult classifyReplay(RoleRuntimeActivationEvent e,RoleRuntimeActivationCommand c){
        if(e.scope().equals(c.scope())&&e.authorizationId().equals(c.authorizationId())
                &&e.authorizationCommit().equals(c.authorizationCommit())&&e.observationEvidenceCommit().equals(c.observationEvidenceCommit())
                &&e.runtimeEnablementEvidenceCommit().equals(c.runtimeEnablementEvidenceCommit())&&e.runtimeReleaseCommit().equals(c.runtimeReleaseCommit())
                &&e.runtimeReleaseTag().equals(c.runtimeReleaseTag())&&e.directoryResultHash().equals(c.directoryResultHash())
                &&e.versionBindingHash().equals(c.versionBindingHash())&&e.manifestHash().equals(c.manifestHash())
                &&e.contentHash().equals(c.contentHash())&&e.structuralFingerprint().equals(c.structuralFingerprint())) return RoleRuntimeActivationResult.ALREADY_ACTIVE;
        throw stale("conflicting activation replay");
    }
    private void validateFrozenBindings(RoleRuntimeActivationCommand c){
        if(!RoleRuntimeActivationEvent.AUTHORIZATION_TYPE.equals(c.authorizationType())
                || !DESIGN_COMMIT.equals(c.designCommit())||!OBSERVATION_COMMIT.equals(c.observationEvidenceCommit())
                ||!ENABLEMENT_EVIDENCE_COMMIT.equals(c.runtimeEnablementEvidenceCommit())||!RELEASE_COMMIT.equals(c.runtimeReleaseCommit())
                ||!RELEASE_TAG.equals(c.runtimeReleaseTag())||!DIRECTORY_HASH.equals(c.directoryResultHash())
                ||!VERSION_HASH.equals(c.versionBindingHash())||!MANIFEST_HASH.equals(c.manifestHash())
                ||!CONTENT_HASH.equals(c.contentHash())||!STRUCTURAL_HASH.equals(c.structuralFingerprint())) throw stale("frozen activation binding mismatch");
    }
    private static IllegalStateException stale(String reason){return new IllegalStateException("ROLE_RUNTIME_ACTIVATION_STALE: "+reason);}
}
