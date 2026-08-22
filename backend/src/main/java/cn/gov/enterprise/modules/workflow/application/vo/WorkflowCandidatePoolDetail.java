package cn.gov.enterprise.modules.workflow.application.vo;

import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only Candidate Pool projection. It never resolves or backfills candidates. */
public record WorkflowCandidatePoolDetail(
        Source source, Long taskId, String assignmentMode, String poolStatus,
        String poolHash, ResolverSummary resolver, int candidateCount,
        LocalDateTime generatedTime, LocalDateTime effectiveTime,
        LocalDateTime expiresTime, List<Member> members) {

    public enum Source {
        FROZEN_CANDIDATE_POOL,
        DIRECT_ASSIGNMENT,
        LEGACY_DIRECT_ASSIGNMENT
    }

    public record ResolverSummary(
            String code, String version, String strategyType,
            String contractHash, String ruleHash) { }

    public record Member(
            Long candidateUserId, String sourceType, String sourceRef,
            Long orgIdSnapshot, Long positionIdSnapshot, Long roleIdSnapshot,
            String eligibilitySnapshot, int sortOrder, String status) {
        static Member from(CandidatePoolMember member) {
            return new Member(member.candidateUserId(), member.sourceType().name(),
                    member.sourceRefSnapshot(), member.orgIdSnapshot(),
                    member.positionIdSnapshot(), member.roleIdSnapshot(),
                    member.eligibilitySnapshot(), member.sortOrder(), member.status().name());
        }
    }

    public WorkflowCandidatePoolDetail {
        members = List.copyOf(members);
    }

    public static WorkflowCandidatePoolDetail frozen(CandidatePool pool) {
        ResolverSummary resolver = new ResolverSummary(pool.resolverCode(), pool.resolverVersion(),
                pool.strategyType().name(), pool.contractHash().value(), pool.ruleHash());
        return new WorkflowCandidatePoolDetail(Source.FROZEN_CANDIDATE_POOL,
                pool.taskId(), pool.assignmentMode().name(), pool.status().name(),
                pool.poolHash().value(), resolver, pool.candidateCount(), pool.generatedTime(),
                pool.effectiveTime(), pool.expiresTime(),
                pool.members().stream().map(Member::from).toList());
    }

    public static WorkflowCandidatePoolDetail direct(WorkflowTask task) {
        Source source = task.nodeExecutionId() == null
                ? Source.LEGACY_DIRECT_ASSIGNMENT : Source.DIRECT_ASSIGNMENT;
        return new WorkflowCandidatePoolDetail(source, task.id(),
                WorkflowTask.AssignmentMode.DIRECT.name(), null, null, null,
                task.assigneeUserId() == null ? 0 : 1, null, null, null, List.of());
    }
}
