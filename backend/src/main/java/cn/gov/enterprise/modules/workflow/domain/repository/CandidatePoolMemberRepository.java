package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import java.util.List;
import java.util.Optional;

/** Append-only Candidate Member persistence for a frozen Pool. */
public interface CandidatePoolMemberRepository {
    void saveAll(List<CandidatePoolMember> members);
    List<CandidatePoolMember> findByPoolId(Long poolId);
    Optional<CandidatePoolMember> findByPoolIdAndUserIdForUpdate(Long poolId, Long userId);
}
