package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentSchemeVersion;
import java.util.List;
import java.util.Optional;

/** Persistence port for the scheme archive and its append-only versions. */
public interface InvestmentSchemeRepository {
    Optional<InvestmentScheme> findByInvestmentId(Long investmentId);
    Optional<InvestmentScheme> findByIdForUpdate(Long schemeId);
    List<InvestmentSchemeVersion> findVersions(Long schemeId);
    int nextVersionNo(Long schemeId);
    void saveArchive(InvestmentScheme scheme);
    void appendVersion(InvestmentSchemeVersion version);
    boolean moveCurrentVersion(InvestmentScheme changed, int expectedVersion);
    Optional<FeasibilityReference> findFeasibilityReference(Long feasibilityVersionId);
    Optional<DueDiligenceReference> findDueDiligenceReference(Long dueDiligencePackageId);

    record FeasibilityReference(
            Long investmentId,
            FeasibilityVersion.Status status,
            FeasibilityVersion.Conclusion conclusion) {
    }

    record DueDiligenceReference(
            Long investmentId,
            DueDiligencePackage.Status status,
            DueDiligencePackage.Conclusion conclusion,
            int openBlockingCount) {
    }
}
