package cn.gov.enterprise.modules.investment.application.security;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Limits the DataScope context to opportunity SQL and prevents alias leakage to downstream modules. */
@Service
public class InvestmentOpportunityScopedLoader {
    private final InvestmentOpportunityRepository repository;

    public InvestmentOpportunityScopedLoader(InvestmentOpportunityRepository repository) {
        this.repository = repository;
    }

    @DataScope(orgField = "io.proposing_org_id", userField = "io.create_by")
    @Transactional(readOnly = true)
    public InvestmentOpportunity load(Long opportunityId) {
        return repository.findById(opportunityId)
                .orElseThrow(() -> new BusinessException(
                        "B0614", "投资机会不存在或无权访问"));
    }

    @DataScope(orgField = "io.proposing_org_id", userField = "io.create_by")
    @Transactional(propagation = Propagation.MANDATORY)
    public InvestmentOpportunity lock(Long opportunityId) {
        return repository.findByIdForUpdate(opportunityId)
                .orElseThrow(() -> new BusinessException(
                        "B0614", "投资机会不存在或无权访问"));
    }
}
