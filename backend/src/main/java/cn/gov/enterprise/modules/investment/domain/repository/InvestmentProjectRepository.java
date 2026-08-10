package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import java.util.Optional;

/** Persistence port for the InvestmentProject aggregate. */
public interface InvestmentProjectRepository {
    Optional<InvestmentProject> findById(Long investmentProjectId);

    Optional<InvestmentProject> findByProjectId(Long projectId);

    /** Minimal identity lookup used before loading protected investment facts. */
    Optional<Long> findProjectIdById(Long investmentProjectId);

    boolean existsByInvestmentNo(String investmentNo);

    void save(InvestmentProject investmentProject);
}
