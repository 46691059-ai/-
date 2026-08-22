package cn.gov.enterprise.modules.workflow.domain.candidate;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable aggregate of a future CANDIDATE_POOL task assignment. */
public record CandidatePool(
        Long id, String poolNo, Long taskId, Long instanceId, Long versionId,
        Long nodeId, Long nodeExecutionId, Long bindingSetId,
        Long resolverBindingId, Long nodeResolverBindingId, Long assignmentSnapshotId,
        ResolverMode assignmentMode, AssignmentStrategy.Type strategyType,
        String resolverCode, String resolverVersion, ResolverContractHash contractHash,
        String ruleHash, LocalDateTime generatedTime, LocalDateTime effectiveTime,
        LocalDateTime expiresTime, CandidatePoolHash poolHash, CandidatePoolStatus status,
        List<CandidatePoolMember> members, String auditInfo, int version) {

    public CandidatePool {
        positive(id, "id");
        poolNo = CandidatePoolMember.required(poolNo, "poolNo", 100);
        positive(taskId, "taskId"); positive(instanceId, "instanceId");
        positive(versionId, "versionId"); positive(nodeId, "nodeId");
        positive(nodeExecutionId, "nodeExecutionId"); positive(bindingSetId, "bindingSetId");
        positive(resolverBindingId, "resolverBindingId");
        positive(nodeResolverBindingId, "nodeResolverBindingId");
        positive(assignmentSnapshotId, "assignmentSnapshotId");
        if (assignmentMode != ResolverMode.CANDIDATE_POOL) {
            throw new IllegalArgumentException("Candidate Pool requires CANDIDATE_POOL mode");
        }
        Objects.requireNonNull(strategyType, "strategyType");
        if (strategyType == AssignmentStrategy.Type.USER) {
            throw new IllegalArgumentException("USER assignments must remain DIRECT");
        }
        resolverCode = CandidatePoolMember.required(resolverCode, "resolverCode", 64);
        resolverVersion = CandidatePoolMember.required(resolverVersion, "resolverVersion", 64);
        Objects.requireNonNull(contractHash, "contractHash");
        CandidatePoolMember.sha256(ruleHash, "ruleHash");
        Objects.requireNonNull(generatedTime, "generatedTime");
        Objects.requireNonNull(effectiveTime, "effectiveTime");
        if (effectiveTime.isBefore(generatedTime)) {
            throw new IllegalArgumentException("effectiveTime must not precede generatedTime");
        }
        if (expiresTime != null && !expiresTime.isAfter(effectiveTime)) {
            throw new IllegalArgumentException("expiresTime must be after effectiveTime");
        }
        Objects.requireNonNull(poolHash, "poolHash");
        Objects.requireNonNull(status, "status");
        List<CandidatePoolMember> supplied = List.copyOf(Objects.requireNonNull(members, "members"));
        if (supplied.isEmpty()) throw new IllegalArgumentException("Candidate Pool must not be empty");
        List<CandidatePoolMember> normalized = supplied.stream()
                .sorted(Comparator.comparingInt(CandidatePoolMember::sortOrder)
                        .thenComparing(CandidatePoolMember::candidateUserId))
                .toList();
        if (normalized.stream().anyMatch(member -> !id.equals(member.poolId())
                || !taskId.equals(member.taskId()) || !instanceId.equals(member.instanceId())
                || !generatedTime.equals(member.generatedTime()))) {
            throw new IllegalArgumentException("Candidate Member ownership/time must match the Pool");
        }
        if (normalized.stream().map(CandidatePoolMember::candidateUserId).distinct().count()
                != normalized.size()) {
            throw new IllegalArgumentException("Candidate users must be unique within a Pool");
        }
        if (normalized.stream().map(CandidatePoolMember::sortOrder).distinct().count()
                != normalized.size()) {
            throw new IllegalArgumentException("Candidate sortOrder must be unique within a Pool");
        }
        CandidatePoolHash expected = CandidatePoolHash.calculate(resolverCode, resolverVersion,
                contractHash.value(), ruleHash, strategyType, normalized);
        if (!expected.equals(poolHash)) throw new IllegalArgumentException("Candidate Pool Hash mismatch");
        members = List.copyOf(normalized);
        auditInfo = CandidatePoolMember.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public static CandidatePool created(
            Long id, String poolNo, Long taskId, Long instanceId, Long versionId,
            Long nodeId, Long nodeExecutionId, Long bindingSetId,
            Long resolverBindingId, Long nodeResolverBindingId, Long assignmentSnapshotId,
            AssignmentStrategy.Type strategyType, String resolverCode, String resolverVersion,
            ResolverContractHash contractHash, String ruleHash, LocalDateTime generatedTime,
            LocalDateTime effectiveTime, LocalDateTime expiresTime,
            List<CandidatePoolMember> members, String auditInfo) {
        CandidatePoolHash hash = CandidatePoolHash.calculate(resolverCode, resolverVersion,
                contractHash.value(), ruleHash, strategyType, members);
        return new CandidatePool(id, poolNo, taskId, instanceId, versionId, nodeId,
                nodeExecutionId, bindingSetId, resolverBindingId, nodeResolverBindingId,
                assignmentSnapshotId, ResolverMode.CANDIDATE_POOL, strategyType,
                resolverCode, resolverVersion, contractHash, ruleHash, generatedTime,
                effectiveTime, expiresTime, hash, CandidatePoolStatus.CREATED,
                members, auditInfo, 0);
    }

    public CandidatePool available() {
        if (status != CandidatePoolStatus.CREATED) {
            throw new IllegalStateException("only a CREATED Candidate Pool can become AVAILABLE");
        }
        return new CandidatePool(id, poolNo, taskId, instanceId, versionId, nodeId,
                nodeExecutionId, bindingSetId, resolverBindingId, nodeResolverBindingId,
                assignmentSnapshotId, assignmentMode, strategyType, resolverCode,
                resolverVersion, contractHash, ruleHash, generatedTime, effectiveTime,
                expiresTime, poolHash, CandidatePoolStatus.AVAILABLE, members,
                auditInfo, version);
    }

    public CandidatePool claimed(LocalDateTime claimTime) {
        Objects.requireNonNull(claimTime, "claimTime");
        if (status != CandidatePoolStatus.AVAILABLE) {
            throw new IllegalStateException("only an AVAILABLE Candidate Pool can be claimed");
        }
        if (claimTime.isBefore(effectiveTime)
                || (expiresTime != null && !claimTime.isBefore(expiresTime))) {
            throw new IllegalStateException("Candidate Pool is outside its effective window");
        }
        return new CandidatePool(id, poolNo, taskId, instanceId, versionId, nodeId,
                nodeExecutionId, bindingSetId, resolverBindingId, nodeResolverBindingId,
                assignmentSnapshotId, assignmentMode, strategyType, resolverCode,
                resolverVersion, contractHash, ruleHash, generatedTime, effectiveTime,
                expiresTime, poolHash, CandidatePoolStatus.CLAIMED, members,
                auditInfo, version);
    }

    public int candidateCount() {
        return members.size();
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
