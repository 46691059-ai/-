package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import java.util.List;
import java.util.Optional;

public interface FeasibilityRepository {
    Optional<InvestmentFeasibility> findByInvestmentId(Long investmentId);
    Optional<InvestmentFeasibility> findByIdForUpdate(Long feasibilityId);
    List<FeasibilityVersion> findVersions(Long feasibilityId);
    int nextVersionNo(Long feasibilityId);
    void saveArchive(InvestmentFeasibility feasibility);
    void appendVersion(FeasibilityVersion version);
    boolean moveCurrentVersion(InvestmentFeasibility changed, int expectedVersion);
}
