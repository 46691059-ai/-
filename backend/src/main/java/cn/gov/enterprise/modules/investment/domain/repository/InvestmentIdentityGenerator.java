package cn.gov.enterprise.modules.investment.domain.repository;

/** Generates application-side identities for Investment aggregates. */
public interface InvestmentIdentityGenerator {
    Long nextId();
}
