package cn.gov.enterprise.modules.workflow.domain.repository;

import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import java.util.Optional;

/** Candidate Pool aggregate header persistence; replacement is intentionally absent. */
public interface CandidatePoolRepository {
    void save(CandidatePool pool);
    Optional<CandidatePool> findByTaskId(Long taskId);
    Optional<CandidatePool> findByTaskIdForUpdate(Long taskId);
    boolean existsByTaskId(Long taskId);
    boolean claim(CandidatePool pool, int expectedVersion);
}
