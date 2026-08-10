package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.DecisionSnapshot;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.model.WorkflowBinding;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.*;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public class InvestmentDecisionRepositoryImpl implements InvestmentDecisionRepository {
    private final InvestmentDecisionMapper decisions; private final DecisionSnapshotMapper snapshots;
    private final WorkflowBindingMapper bindings;
    public InvestmentDecisionRepositoryImpl(InvestmentDecisionMapper d, DecisionSnapshotMapper s, WorkflowBindingMapper b) {
        decisions=d; snapshots=s; bindings=b;
    }
    @Override public void save(InvestmentDecisionCase d) {
        var e=new InvestmentDecisionEntity(); e.setId(d.id()); e.setInvestmentId(d.investmentId());
        e.setDecisionNo(d.decisionNo()); e.setDecisionSubject(d.subject()); e.setApprovalStatus(d.status().name());
        e.setDecisionType("WORKFLOW"); e.setMeetingDate(LocalDate.now()); e.setDecisionResult("PENDING");
        e.setDecisionPackageVersion(1); e.setMajorDecisionApplicable(0); e.setPartyPreStudyRequired(0); e.setDeleteToken(0L);
        decisions.insert(e);
    }
    @Override public Optional<InvestmentDecisionCase> findById(Long id){return Optional.ofNullable(decisions.selectById(id)).map(this::domain);}
    @Override public Optional<InvestmentDecisionCase> findByIdForUpdate(Long id){return Optional.ofNullable(decisions.selectForUpdate(id)).map(this::domain);}
    @Override public boolean existsByDecisionNo(String no){return decisions.selectCount(new LambdaQueryWrapper<InvestmentDecisionEntity>().eq(InvestmentDecisionEntity::getDecisionNo,no))>0;}
    @Override public int nextSnapshotVersion(Long id){return snapshots.nextVersion(id);}
    @Override public int nextAttemptNo(Long id){return bindings.nextAttempt(id);}
    @Override public DecisionMaterials requireFrozenMaterials(Long id){
        var r=decisions.selectFrozenMaterials(id); if(r==null) throw new BusinessException("B0680","缺少已冻结的决策材料");
        return new DecisionMaterials(r.getSchemeVersionId(),r.getSchemeStatus(),r.getSchemeHash(),r.getFeasibilityVersionId(),r.getFeasibilityStatus(),r.getFeasibilityConclusion(),r.getFeasibilityHash(),r.getDueDiligencePackageId(),r.getDueDiligenceStatus(),r.getDueDiligenceConclusion(),r.getOpenBlockingCount()==null?0:r.getOpenBlockingCount(),r.getDueDiligenceHash(),r.getEnterpriseId());
    }
    @Override public void saveSnapshot(DecisionSnapshot s){var e=new DecisionSnapshotEntity(); e.setId(s.id());e.setDecisionId(s.decisionId());e.setSnapshotVersion(s.snapshotVersion());e.setSnapshotStatus("FROZEN");e.setDecisionPackageVersion(s.snapshotVersion());e.setSchemeVersionId(s.schemeVersionId());e.setSchemeContentHash(s.schemeHash());e.setFeasibilityVersionId(s.feasibilityVersionId());e.setFeasibilityContentHash(s.feasibilityHash());e.setDueDiligencePackageId(s.dueDiligencePackageId());e.setDueDiligenceContentHash(s.dueDiligenceHash());e.setRouteCode(s.routeCode());e.setRouteRuleVersion(s.routeRuleVersion());e.setRouteSnapshotHash(s.routeHash());e.setMajorDecisionApplicable(0);e.setPartyPreStudyRequired(0);e.setFinalDecisionBody("MANAGEMENT");e.setRiskLevelSnapshot("UNKNOWN");e.setRiskGateResult("PENDING");e.setFrozenBy(s.frozenBy());e.setFrozenTime(s.frozenTime());e.setSnapshotHash(s.snapshotHash());e.setDeleteToken(0L);snapshots.insert(e);}
    @Override public void saveBinding(WorkflowBinding b,Long org,String hash,String key,int ver,String req,String trace){var e=new WorkflowBindingEntity();e.setId(b.id());e.setDecisionId(b.decisionId());e.setSnapshotId(b.snapshotId());e.setSnapshotHash(hash);e.setAttemptNo(b.attemptNo());e.setBusinessType("INVESTMENT_DECISION");e.setBusinessId(String.valueOf(b.decisionId()));e.setBusinessKey("INVESTMENT_DECISION:"+b.decisionId()+":"+b.attemptNo());e.setEnterpriseId(org);e.setDefinitionKey(key);e.setDefinitionVersion(ver);e.setIdempotencyKey(b.idempotencyKey());e.setRequestHash(req);e.setWorkflowStatus(b.status().name());e.setLastEventSequence(0L);e.setTraceId(trace);e.setDeleteToken(0L);bindings.insert(e);}
    @Override public Optional<WorkflowBinding> findCurrentBinding(Long id){return Optional.ofNullable(bindings.selectCurrent(id)).map(this::binding);}
    @Override public void updateDecisionState(Long id,InvestmentDecisionCase.Status st,Long snapshot){var e=new InvestmentDecisionEntity();e.setId(id);e.setApprovalStatus(st.name());e.setCurrentSnapshotId(snapshot);if(st==InvestmentDecisionCase.Status.SUBMITTED)e.setSubmittedTime(java.time.LocalDateTime.now());if(st==InvestmentDecisionCase.Status.APPROVED||st==InvestmentDecisionCase.Status.REJECTED||st==InvestmentDecisionCase.Status.WITHDRAWN){e.setDecisionResult(st.name());e.setCompletedTime(java.time.LocalDateTime.now());}decisions.updateById(e);}
    private InvestmentDecisionCase domain(InvestmentDecisionEntity e){return new InvestmentDecisionCase(e.getId(),e.getInvestmentId(),e.getDecisionNo(),e.getDecisionSubject(),InvestmentDecisionCase.Status.fromStorage(e.getApprovalStatus()),e.getCurrentSnapshotId(),e.getVersion()==null?0:e.getVersion());}
    private WorkflowBinding binding(WorkflowBindingEntity e){return new WorkflowBinding(e.getId(),e.getDecisionId(),e.getSnapshotId(),e.getAttemptNo(),e.getWorkflowInstanceId(),e.getIdempotencyKey(),WorkflowBinding.Status.valueOf(e.getWorkflowStatus()),e.getLastEventSequence()==null?0:e.getLastEventSequence(),e.getVersion()==null?0:e.getVersion());}
}
