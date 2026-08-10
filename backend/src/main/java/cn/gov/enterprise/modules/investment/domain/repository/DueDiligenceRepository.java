package cn.gov.enterprise.modules.investment.domain.repository;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceType;
import java.util.List;
import java.util.Optional;

public interface DueDiligenceRepository {
    int nextPackageVersion(Long investmentId);
    void savePackage(DueDiligencePackage dueDiligencePackage);
    Optional<DueDiligencePackage> findPackage(Long packageId);
    Optional<Long> findInvestmentIdByPackageId(Long packageId);
    List<DueDiligencePackage> findPackages(Long investmentId);
    int nextReportVersion(Long packageId, DueDiligenceType type);
    void appendReport(DueDiligenceReport report);
    Optional<DueDiligenceReport> findReport(Long reportId);
    Optional<Long> findInvestmentIdByReportId(Long reportId);
    List<DueDiligenceReport> findReports(Long packageId);
    void saveItem(DueDiligenceItem item);
    List<DueDiligenceItem> findItems(Long reportId);
    boolean existsItemNo(Long reportId, String itemNo);
}
