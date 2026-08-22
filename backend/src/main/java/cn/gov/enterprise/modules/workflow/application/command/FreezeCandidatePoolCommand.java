package cn.gov.enterprise.modules.workflow.application.command;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import java.time.LocalDateTime;
import java.util.List;

/** Internal command produced by a future CANDIDATE_POOL resolver; it is not a REST DTO. */
public record FreezeCandidatePoolCommand(
        Long taskId, LocalDateTime generatedTime, LocalDateTime effectiveTime,
        LocalDateTime expiresTime, String auditInfo, List<Member> members) {

    public record Member(
            Long candidateUserId, AssignmentStrategy.Type sourceType,
            String sourceRefSnapshot, Long orgIdSnapshot,
            Long positionIdSnapshot, Long roleIdSnapshot,
            String eligibilitySnapshot, int sortOrder, String auditInfo) { }
}
