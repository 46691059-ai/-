package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNodeExecution;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolMemberRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.CandidatePoolRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowNodeExecutionRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskAssignmentSnapshotRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowTaskRepository;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateUser;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.runtime.PreparedRoleCandidatePool;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactionally materializes a verified ROLE draft; Claim/Admission are not invoked. */
@Service
public class RoleCandidatePoolTransactionService {
    private final WorkflowTaskRepository tasks;
    private final WorkflowTaskAssignmentSnapshotRepository assignments;
    private final CandidatePoolRepository pools;
    private final CandidatePoolMemberRepository members;
    private final WorkflowNodeExecutionRepository executions;
    private final WorkflowIdentityGenerator ids;

    public RoleCandidatePoolTransactionService(
            WorkflowTaskRepository tasks,
            WorkflowTaskAssignmentSnapshotRepository assignments,
            CandidatePoolRepository pools,
            CandidatePoolMemberRepository members,
            WorkflowNodeExecutionRepository executions,
            WorkflowIdentityGenerator ids) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.pools = pools;
        this.members = members;
        this.executions = executions;
        this.ids = ids;
    }

    @Transactional
    public CandidatePool freeze(PreparedRoleCandidatePool prepared) {
        WorkflowNodeExecution current = executions.findByIdForUpdate(
                        prepared.execution().id())
                .orElseThrow(() -> blocked("node execution does not exist"));
        requireActivationUnchanged(prepared.execution(), current);

        var existingTask = tasks.findByNodeExecutionId(current.id());
        if (existingTask.isPresent()) {
            return requireIdempotentExisting(prepared, existingTask.get());
        }

        long taskId = ids.nextId();
        WorkflowTask task = WorkflowTask.pendingCandidatePool(taskId,
                "WFT-" + taskId, prepared.instance(), current,
                prepared.evidence().canonical(), null);
        List<Long> candidateUserIds = prepared.candidates().candidateUsers().stream()
                .map(RoleCandidatePoolTransactionService::numericUserId)
                .sorted().toList();
        long assignmentId = ids.nextId();
        AssignmentSnapshot assignment = new AssignmentSnapshot(assignmentId,
                taskId, prepared.instance().id(), prepared.instance().versionId(),
                current.nodeId(), current.id(), AssignmentStrategy.Type.ROLE,
                AssignmentStrategy.Type.ROLE, prepared.evidence().canonical(),
                candidateUserIds, current.activatedTime(),
                "ROLE_DIRECTORY_RUNTIME_BRIDGE", prepared.context().traceId(), 0);

        long poolId = ids.nextId();
        List<CandidatePoolMember> frozenMembers = prepared.candidates().candidateUsers()
                .stream().sorted(Comparator.comparing(RoleCandidateUser::userId))
                .map(candidate -> candidateMember(prepared, candidate, poolId,
                        taskId, candidateUserIds.indexOf(numericUserId(candidate)) + 1))
                .toList();
        CandidatePool pool = CandidatePool.created(poolId, "WCP-" + poolId,
                taskId, prepared.instance().id(), prepared.instance().versionId(),
                current.nodeId(), current.id(), prepared.nodeBinding().bindingSetId(),
                prepared.resolverBinding().id(), prepared.nodeBinding().id(),
                assignmentId, AssignmentStrategy.Type.ROLE,
                prepared.resolverBinding().resolverCode().value(),
                prepared.resolverBinding().resolverVersion().value(),
                prepared.resolverBinding().contractHash(),
                prepared.nodeBinding().ruleSnapshotHash(), current.activatedTime(),
                current.activatedTime(), null, frozenMembers,
                "ROLE_DIRECTORY_RUNTIME_BRIDGE|" + prepared.evidence().evidenceHash())
                .available();

        tasks.save(task);
        assignments.save(assignment);
        pools.save(pool);
        members.saveAll(pool.members());
        return pool;
    }

    private CandidatePool requireIdempotentExisting(
            PreparedRoleCandidatePool prepared, WorkflowTask task) {
        CandidatePool pool = pools.findByTaskId(task.id())
                .orElseThrow(() -> blocked("partial ROLE Candidate Pool state detected"));
        List<Long> expectedUsers = prepared.candidates().candidateUsers().stream()
                .map(RoleCandidatePoolTransactionService::numericUserId).sorted().toList();
        List<Long> actualUsers = pool.members().stream()
                .map(CandidatePoolMember::candidateUserId).sorted().toList();
        requireFrozenMatch(task.assignmentMode()
                == WorkflowTask.AssignmentMode.CANDIDATE_POOL, "assignment mode");
        requireFrozenMatch(task.candidateSnapshot().equals(
                prepared.evidence().canonical()), "candidate snapshot");
        requireFrozenMatch(pool.resolverCode().equals(
                prepared.context().resolverCode()), "resolver code");
        requireFrozenMatch(pool.resolverVersion().equals(
                prepared.context().resolverVersion()), "resolver version");
        requireFrozenMatch(pool.contractHash().value().equals(
                prepared.context().resolverContractHash()), "resolver contract hash");
        requireFrozenMatch(actualUsers.equals(expectedUsers), "candidate users");
        requireFrozenMatch(pool.members().stream().allMatch(member ->
                member.eligibilitySnapshot().startsWith(
                        prepared.evidence().canonical())), "member evidence");
        return pool;
    }

    private void requireFrozenMatch(boolean matches, String field) {
        if (!matches) throw blocked("existing Candidate Pool " + field + " drift");
    }

    private CandidatePoolMember candidateMember(
            PreparedRoleCandidatePool prepared, RoleCandidateUser candidate,
            long poolId, long taskId, int sortOrder) {
        long userId = numericUserId(candidate);
        String evidence = prepared.evidence().canonical()
                + "\ncandidateUserId=" + candidate.userId().length() + ":"
                + candidate.userId() + "\nassignments="
                + assignmentEvidence(candidate.assignments());
        return new CandidatePoolMember(ids.nextId(), poolId, taskId,
                prepared.instance().id(), userId, AssignmentStrategy.Type.ROLE,
                prepared.evidence().sourceReference(),
                prepared.evidence().organizationId(), null, null, evidence,
                ResolverContractHash.sha256(evidence).value(), sortOrder,
                prepared.execution().activatedTime(), CandidateMemberStatus.INCLUDED,
                "ROLE_DIRECTORY_FROZEN_MEMBER", 0);
    }

    private String assignmentEvidence(List<RoleDirectoryMember> assignments) {
        return assignments.stream()
                .sorted(Comparator.comparing(RoleDirectoryMember::assignmentId))
                .map(item -> item.assignmentId() + "@" + item.sourceType().name()
                        + "@" + item.sourceRef())
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private static long numericUserId(RoleCandidateUser candidate) {
        try {
            long value = Long.parseLong(candidate.userId());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Directory candidate userId must be a positive numeric key", exception);
        }
    }

    private void requireActivationUnchanged(
            WorkflowNodeExecution prepared, WorkflowNodeExecution current) {
        if (!prepared.id().equals(current.id())
                || prepared.version() != current.version()
                || current.status() != WorkflowNodeExecution.Status.ACTIVE
                || !prepared.activatedTime().equals(current.activatedTime())) {
            throw blocked("node activation changed while Directory was resolving");
        }
    }

    private BusinessException blocked(String message) {
        return new BusinessException("B2680", message);
    }
}
