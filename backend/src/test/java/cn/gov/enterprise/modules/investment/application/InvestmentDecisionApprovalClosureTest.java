package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.ReviewDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.command.SubmitConditionRectificationCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDecisionConditionCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionClosureService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDecisionConditionService;
import cn.gov.enterprise.modules.investment.application.workflow.WorkflowEvent;
import cn.gov.enterprise.modules.investment.domain.model.DecisionCondition;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentDecisionCase;
import cn.gov.enterprise.modules.investment.domain.repository.DecisionConditionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentDecisionRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowBindingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowBindingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.WorkflowInboxMapper;
import cn.gov.enterprise.modules.investment.infrastructure.workflow.WorkflowInboxService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class InvestmentDecisionApprovalClosureTest {
    @Test
    void duplicateAndOutOfOrderWorkflowEventsMustNotChangeDecision() {
        WorkflowInboxMapper inbox = mock(WorkflowInboxMapper.class);
        WorkflowBindingMapper bindings = mock(WorkflowBindingMapper.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        WorkflowInboxService service = new WorkflowInboxService(inbox, bindings, decisions, ids);

        WorkflowInboxEntity existing = new WorkflowInboxEntity();
        existing.setPayloadHash("hash");
        when(inbox.selectByEventId("evt-duplicate")).thenReturn(existing);
        assertThat(service.receive(event("evt-duplicate", 2, "APPROVAL_APPROVED", "APPROVED"), "{}", "hash"))
                .isEqualTo(WorkflowInboxService.Result.DUPLICATE);
        verifyNoInteractions(bindings, decisions);

        reset(inbox);
        WorkflowBindingEntity binding = binding(1L);
        when(bindings.selectByInstanceForUpdate("wf-1")).thenReturn(binding);
        when(ids.nextId()).thenReturn(100L);
        assertThat(service.receive(event("evt-3", 3, "APPROVAL_APPROVED", "APPROVED")))
                .isEqualTo(WorkflowInboxService.Result.BUFFERED);
        verify(decisions, never()).updateDecisionState(any(), any(), any());
        verify(inbox).insert(argThat((WorkflowInboxEntity row) ->
                "BUFFERED".equals(row.getProcessStatus()) && row.getEventSequence() == 3L));
    }

    @Test
    void approvedEventMustDriveDecisionToApprovedAndAdvanceSequence() {
        WorkflowInboxMapper inbox = mock(WorkflowInboxMapper.class);
        WorkflowBindingMapper bindings = mock(WorkflowBindingMapper.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        WorkflowBindingEntity binding = binding(1L);
        when(bindings.selectByInstanceForUpdate("wf-1")).thenReturn(binding);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision(InvestmentDecisionCase.Status.IN_APPROVAL)));
        when(ids.nextId()).thenReturn(101L);

        WorkflowInboxService service = new WorkflowInboxService(inbox, bindings, decisions, ids);
        assertThat(service.receive(event("evt-2", 2, "APPROVAL_APPROVED", "APPROVED")))
                .isEqualTo(WorkflowInboxService.Result.PROCESSED);
        verify(decisions).updateDecisionState(10L, InvestmentDecisionCase.Status.APPROVED, 11L);
        assertThat(binding.getLastEventSequence()).isEqualTo(2L);
        assertThat(binding.getWorkflowStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void exceptionalEventMustBeRecordedWithoutForgingApprovalResult() {
        WorkflowInboxMapper inbox = mock(WorkflowInboxMapper.class);
        WorkflowBindingMapper bindings = mock(WorkflowBindingMapper.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        when(bindings.selectByInstanceForUpdate("wf-1")).thenReturn(binding(1L));
        when(ids.nextId()).thenReturn(102L);
        WorkflowInboxService service = new WorkflowInboxService(inbox, bindings, decisions, ids);

        assertThat(service.receive(event("evt-error", 2, "PROCESS_EXCEPTION", "REMOTE_TIMEOUT")))
                .isEqualTo(WorkflowInboxService.Result.FAILED);
        verify(decisions, never()).updateDecisionState(any(), any(), any());
        verify(inbox).insert(argThat((WorkflowInboxEntity row) ->
                "FAILED".equals(row.getProcessStatus()) && "REMOTE_TIMEOUT".equals(row.getFailureCode())));
    }

    @Test
    void conditionMustSupportRectificationReviewAndClose() {
        DecisionConditionRepository conditions = mock(DecisionConditionRepository.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        InvestmentLifecycleStagePolicy lifecycle = mock(InvestmentLifecycleStagePolicy.class);
        CurrentSecurityContext security = mock(CurrentSecurityContext.class);
        when(security.userId()).thenReturn(9L);
        when(ids.nextId()).thenReturn(201L, 202L, 203L, 204L);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision(InvestmentDecisionCase.Status.APPROVED)));
        DecisionCondition open = condition(DecisionCondition.Status.OPEN);
        DecisionCondition inProgress = condition(DecisionCondition.Status.IN_PROGRESS);
        DecisionCondition submitted = condition(DecisionCondition.Status.SUBMITTED);
        DecisionCondition verified = condition(DecisionCondition.Status.VERIFIED);
        when(conditions.findByIdForUpdate(20L)).thenReturn(
                Optional.of(open), Optional.of(inProgress), Optional.of(submitted), Optional.of(verified));
        InvestmentDecisionConditionService service = new InvestmentDecisionConditionService(
                conditions, decisions, ids, lifecycle, security);

        assertThat(service.start(20L).status()).isEqualTo(DecisionCondition.Status.IN_PROGRESS);
        assertThat(service.submit(20L, new SubmitConditionRectificationCommand("完成整改", 30L, "submit-1")).status())
                .isEqualTo(DecisionCondition.Status.SUBMITTED);
        assertThat(service.review(20L, new ReviewDecisionConditionCommand(
                ReviewDecisionConditionCommand.ReviewResult.APPROVED, "复核通过", "review-1")).status())
                .isEqualTo(DecisionCondition.Status.VERIFIED);
        assertThat(service.close(20L).status()).isEqualTo(DecisionCondition.Status.CLOSED);
        verify(conditions, times(4)).saveAction(any(), any(), any(), eq(9L), any(), any(), any());
    }

    @Test
    void approvedDecisionCanCreateConditionWithValidatedOwnershipAndAssignee() {
        DecisionConditionRepository conditions = mock(DecisionConditionRepository.class);
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        InvestmentIdentityGenerator ids = mock(InvestmentIdentityGenerator.class);
        InvestmentLifecycleStagePolicy lifecycle = mock(InvestmentLifecycleStagePolicy.class);
        CurrentSecurityContext security = mock(CurrentSecurityContext.class);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision(InvestmentDecisionCase.Status.APPROVED)));
        when(conditions.sourceNodeBelongsToDecision(21L, 10L)).thenReturn(true);
        when(conditions.assignmentTargetsExist(22L, 23L)).thenReturn(true);
        when(ids.nextId()).thenReturn(20L, 30L);
        when(security.userId()).thenReturn(9L);
        InvestmentDecisionConditionService service = new InvestmentDecisionConditionService(
                conditions, decisions, ids, lifecycle, security);

        DecisionCondition created = service.create(10L, new CreateDecisionConditionCommand(
                21L, "C-1", "完成风险整改", true, 22L, 23L,
                LocalDate.now().plusDays(10), "HIGH"));
        assertThat(created.status()).isEqualTo(DecisionCondition.Status.OPEN);
        verify(conditions).save(created);
        verify(conditions).saveAction(eq(30L), isNull(), eq(created), eq(9L), any(), isNull(), any());
    }

    @Test
    void majorOpenRiskMustBlockArchive() {
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        DecisionConditionRepository conditions = mock(DecisionConditionRepository.class);
        InvestmentLifecycleStagePolicy lifecycle = mock(InvestmentLifecycleStagePolicy.class);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision(InvestmentDecisionCase.Status.APPROVED)));
        when(conditions.hasOpenMajorRisk(10L)).thenReturn(true);
        InvestmentDecisionClosureService service = new InvestmentDecisionClosureService(decisions, conditions, lifecycle);

        assertThatThrownBy(() -> service.archive(10L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("重大风险");
        verify(decisions, never()).updateDecisionState(any(), any(), any());
    }

    @Test
    void approvedDecisionWithoutOpenConditionsOrRiskCanBeArchived() {
        InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
        DecisionConditionRepository conditions = mock(DecisionConditionRepository.class);
        InvestmentLifecycleStagePolicy lifecycle = mock(InvestmentLifecycleStagePolicy.class);
        when(decisions.findByIdForUpdate(10L)).thenReturn(Optional.of(decision(InvestmentDecisionCase.Status.APPROVED)));
        InvestmentDecisionClosureService service = new InvestmentDecisionClosureService(decisions, conditions, lifecycle);
        assertThat(service.archive(10L).status()).isEqualTo(InvestmentDecisionCase.Status.ARCHIVED);
        verify(decisions).updateDecisionState(10L, InvestmentDecisionCase.Status.ARCHIVED, 11L);
    }

    @Test
    void closureOperationsMustDeclareDedicatedPermissions() throws Exception {
        PreAuthorize condition = InvestmentDecisionConditionService.class
                .getMethod("create", Long.class,
                        cn.gov.enterprise.modules.investment.application.command.CreateDecisionConditionCommand.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize archive = InvestmentDecisionClosureService.class.getMethod("archive", Long.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(condition.value()).contains("investment:decision:condition");
        assertThat(archive.value()).contains("investment:decision:archive");
    }

    private static WorkflowEvent event(String id, long sequence, String type, String result) {
        return new WorkflowEvent(id, sequence, type, "wf-1", 10L, 11L, 1,
                result, "9", LocalDateTime.now(), "trace");
    }

    private static WorkflowBindingEntity binding(long sequence) {
        WorkflowBindingEntity entity = new WorkflowBindingEntity();
        entity.setId(12L); entity.setDecisionId(10L); entity.setSnapshotId(11L);
        entity.setAttemptNo(1); entity.setWorkflowInstanceId("wf-1");
        entity.setWorkflowStatus("RUNNING"); entity.setLastEventSequence(sequence);
        return entity;
    }

    private static InvestmentDecisionCase decision(InvestmentDecisionCase.Status status) {
        return new InvestmentDecisionCase(10L, 15L, "D-10", "重大投资", status, 11L, 0);
    }

    private static DecisionCondition condition(DecisionCondition.Status status) {
        return new DecisionCondition(20L, 10L, 11L, 21L, "C-1", "完成风险整改", true,
                22L, 23L, LocalDate.now().plusDays(10), "HIGH", status, 0);
    }
}
