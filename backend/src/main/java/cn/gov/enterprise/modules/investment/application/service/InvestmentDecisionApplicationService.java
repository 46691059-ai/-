package cn.gov.enterprise.modules.investment.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentDecisionCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowGateway;
import cn.gov.enterprise.modules.investment.domain.model.*;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowOutboxService;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowProperties;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentDecisionApplicationService {
    private final InvestmentDecisionRepository repository; private final InvestmentIdentityGenerator ids;
    private final InvestmentLifecycleStagePolicy lifecycle; private final CurrentSecurityContext security;
    private final WorkflowProperties properties; private final WorkflowOutboxService outbox; private final WorkflowGateway workflow;
    public InvestmentDecisionApplicationService(InvestmentDecisionRepository repository, InvestmentIdentityGenerator ids,
            InvestmentLifecycleStagePolicy lifecycle, CurrentSecurityContext security, WorkflowProperties properties,
            WorkflowOutboxService outbox, WorkflowGateway workflow) {
        this.repository=repository;this.ids=ids;this.lifecycle=lifecycle;this.security=security;
        this.properties=properties;this.outbox=outbox;this.workflow=workflow;
    }

    @PreAuthorize("hasAuthority('investment:decision:create')")
    @Transactional
    public InvestmentDecisionCase createDecision(CreateInvestmentDecisionCommand c) {
        if(c==null) throw new BusinessException("B0680","决策参数不能为空");
        lifecycle.requireStage(c.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        if(repository.existsByDecisionNo(c.decisionNo())) throw new BusinessException("B0681","决策编号已存在");
        var d=new InvestmentDecisionCase(ids.nextId(),c.investmentId(),c.decisionNo(),c.subject(),InvestmentDecisionCase.Status.DRAFT,null,0);
        repository.save(d); return d;
    }

    @PreAuthorize("hasAuthority('investment:decision:submit')")
    @Transactional
    public SubmissionResult submitDecision(Long decisionId) {
        var d=repository.findByIdForUpdate(decisionId).orElseThrow(()->new BusinessException("B0684","决策事项不存在"));
        lifecycle.requireStage(d.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        if(d.submissionInProgress()) {
            var b=repository.findCurrentBinding(decisionId).orElseThrow(()->new BusinessException("B0685","提交绑定缺失"));
            return new SubmissionResult(d.id(),d.currentSnapshotId(),b.attemptNo(),d.status().name(),true);
        }
        if(d.status()!=InvestmentDecisionCase.Status.DRAFT && d.status()!=InvestmentDecisionCase.Status.WITHDRAWN)
            throw new BusinessException("B0682","当前决策状态不允许提交");
        var m=repository.requireFrozenMaterials(d.investmentId()); validateMaterials(m);
        int snapshotVersion=repository.nextSnapshotVersion(d.id()); int attempt=repository.nextAttemptNo(d.id());
        LocalDateTime now=LocalDateTime.now(); String route="STANDARD_INVESTMENT_DECISION"; String rule="1.0";
        String routeHash=sha(route+"|"+rule); String snapshotHash=sha(d.id()+"|"+snapshotVersion+"|"+m.schemeVersionId()+"|"+m.schemeHash()+"|"+m.feasibilityVersionId()+"|"+m.feasibilityHash()+"|"+m.dueDiligencePackageId()+"|"+m.dueDiligenceHash()+"|"+routeHash);
        var snapshot=new DecisionSnapshot(ids.nextId(),d.id(),snapshotVersion,m.schemeVersionId(),m.schemeHash(),m.feasibilityVersionId(),m.feasibilityHash(),m.dueDiligencePackageId(),m.dueDiligenceHash(),route,rule,routeHash,security.userId(),now,snapshotHash);
        repository.saveSnapshot(snapshot);
        String idempotency="investment-decision:"+d.id()+":"+attempt; String requestHash=sha(idempotency+"|"+snapshotHash);
        var binding=new WorkflowBinding(ids.nextId(),d.id(),snapshot.id(),attempt,null,idempotency,WorkflowBinding.Status.STARTING,0,0);
        repository.saveBinding(binding,m.enterpriseId(),snapshotHash,properties.decisionDefinitionKey(),properties.decisionDefinitionVersion(),requestHash,MDC.get("traceId"));
        InvestmentDecisionCase submitted = d.transitionTo(InvestmentDecisionCase.Status.SUBMITTED);
        repository.updateDecisionState(d.id(),submitted.status(),snapshot.id());
        var command=new WorkflowGateway.StartCommand("INVESTMENT_DECISION",String.valueOf(d.id()),idempotency,m.enterpriseId(),snapshot.id(),snapshotHash,attempt,properties.decisionDefinitionKey(),properties.decisionDefinitionVersion(),security.userId(),java.util.Map.of("decisionId",d.id(),"snapshotId",snapshot.id()),idempotency,MDC.get("traceId"));
        outbox.enqueueStart(d.id(),binding.id(),command);
        return new SubmissionResult(d.id(),snapshot.id(),attempt,"SUBMITTED",false);
    }

    @PreAuthorize("hasAuthority('investment:decision:withdraw')")
    @Transactional
    public void withdrawDecision(Long id,String reason) {
        var d=repository.findByIdForUpdate(id).orElseThrow(()->new BusinessException("B0684","决策事项不存在"));
        lifecycle.requireStage(d.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        var b=repository.findCurrentBinding(id).orElseThrow(()->new BusinessException("B0685","Workflow绑定不存在"));
        if(b.workflowInstanceId()==null || b.status()!=WorkflowBinding.Status.RUNNING) throw new BusinessException("B0682","当前流程不可撤回");
        outbox.enqueueWithdraw(d.id(),b.id(),b.workflowInstanceId(),reason,b.idempotencyKey()+":withdraw");
    }

    @PreAuthorize("hasAuthority('investment:decision:view')")
    @Transactional(readOnly=true)
    public DecisionStatus queryStatus(Long id) {
        var d=repository.findById(id).orElseThrow(()->new BusinessException("B0684","决策事项不存在"));
        lifecycle.requireStage(d.investmentId(), InvestmentLifecycleStagePolicy.DECISION);
        var b=repository.findCurrentBinding(id).orElse(null);
        return new DecisionStatus(d.id(),d.status().name(),d.currentSnapshotId(),b==null?null:b.workflowInstanceId(),b==null?null:b.status().name());
    }
    @PreAuthorize("hasAuthority('investment:decision:view')") public WorkflowGateway.WorkflowInstance queryWorkflow(Long id){var s=queryStatus(id);if(s.workflowInstanceId()==null)throw new BusinessException("B0685","Workflow实例尚未建立");return workflow.query(s.workflowInstanceId());}
    @PreAuthorize("hasAuthority('investment:decision:view')") public List<WorkflowGateway.WorkflowTask> queryTasks(Long id){var s=queryStatus(id);if(s.workflowInstanceId()==null)return List.of();return workflow.queryTasks(s.workflowInstanceId());}

    private static void validateMaterials(InvestmentDecisionRepository.DecisionMaterials m){
        if(!"FROZEN".equals(m.schemeStatus())||blank(m.schemeHash()))throw new BusinessException("B0686","投资方案必须冻结且具有内容哈希");
        if(!"FROZEN".equals(m.feasibilityStatus())||!"RECOMMENDED".equals(m.feasibilityConclusion())||blank(m.feasibilityHash()))throw new BusinessException("B0686","可研版本必须冻结并建议实施");
        if(!"FROZEN".equals(m.dueDiligenceStatus())||!"PASS".equals(m.dueDiligenceConclusion())||m.openBlockingCount()!=0||blank(m.dueDiligenceHash()))throw new BusinessException("B0686","尽调必须冻结、通过且无阻断问题");
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record SubmissionResult(Long decisionId,Long snapshotId,int attemptNo,String status,boolean duplicate){}
    public record DecisionStatus(Long decisionId,String status,Long snapshotId,String workflowInstanceId,String workflowStatus){}
}
