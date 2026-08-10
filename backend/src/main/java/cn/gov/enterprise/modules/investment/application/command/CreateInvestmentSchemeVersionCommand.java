package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInvestmentSchemeVersionCommand(
        String schemeNo,
        String schemeName,
        Long investmentSubjectOrgId,
        Long investeeCompanyId,
        InvestmentProject.InvestmentType investmentType,
        BigDecimal totalAmount,
        String currencyCode,
        InvestmentProject.InvestmentMethod investmentMethod,
        String contributionScheduleSummary,
        BigDecimal preInvestmentRatio,
        BigDecimal postInvestmentRatio,
        String shareType,
        String controlType,
        String governanceArrangement,
        String cooperationMode,
        String partnerArrangement,
        BigDecimal valuationAmount,
        LocalDate valuationBaseDate,
        String incomeDistribution,
        String exitType,
        String exitPlan,
        String conditionsPrecedent,
        Long feasibilityVersionId,
        Long dueDiligencePackageId,
        Long primaryFileId,
        List<CreateSchemeFundingCommand> fundingSources) {
}
