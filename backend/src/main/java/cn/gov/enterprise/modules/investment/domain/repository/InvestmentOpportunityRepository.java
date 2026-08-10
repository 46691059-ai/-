package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import java.util.Optional;

/** Persistence port for investment opportunities. */
public interface InvestmentOpportunityRepository {
    Optional<InvestmentOpportunity> findById(Long opportunityId);

    /** Locks one opportunity row for a conversion or state transition transaction. */
    Optional<InvestmentOpportunity> findByIdForUpdate(Long opportunityId);

    boolean existsByOpportunityNo(String opportunityNo);

    void save(InvestmentOpportunity opportunity);

    /** Optimistic state update. Returns false when status/version changed concurrently. */
    boolean updateState(
            InvestmentOpportunity opportunity,
            InvestmentOpportunity.Status expectedStatus,
            int expectedVersion,
            String operator);
}
