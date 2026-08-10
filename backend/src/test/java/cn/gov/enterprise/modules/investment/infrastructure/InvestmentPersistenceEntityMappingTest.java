package cn.gov.enterprise.modules.investment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentOpportunityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentProjectEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentFeasibilityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.FeasibilityVersionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligencePackageEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceReportEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceItemEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeFundingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeVersionEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InvestmentPersistenceEntityMappingTest {

    @Test
    void projectEntityShouldMapTheV240TableAndRequiredFields() {
        assertThat(InvestmentProjectEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("investment_project");
        assertThat(fieldNames(InvestmentProjectEntity.class)).contains(
                "investmentNo", "projectId", "investmentName", "investmentType",
                "investmentMethod", "cooperationMode", "totalAmount", "investmentRatio",
                "partnerSummary", "investeeCompanyId", "approvalStatus", "status");
        assertBaseContract(InvestmentProjectEntity.class);
    }

    @Test
    void opportunityEntityShouldMapTheV240TableAndRequiredFields() {
        assertThat(InvestmentOpportunityEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("investment_opportunity");
        assertThat(fieldNames(InvestmentOpportunityEntity.class)).contains(
                "opportunityNo", "investmentId", "opportunityName", "sourceType",
                "proposingOrgId", "proposerId", "investmentDirection", "partnerSummary",
                "preliminaryAmount", "preliminaryIncome", "status", "convertedTime");
        assertBaseContract(InvestmentOpportunityEntity.class);
    }

    @Test
    void argumentationEntitiesShouldMapTheV240TablesAndBaseContract() {
        assertTable(InvestmentFeasibilityEntity.class, "investment_feasibility");
        assertTable(FeasibilityVersionEntity.class, "investment_feasibility_version");
        assertTable(DueDiligencePackageEntity.class, "investment_due_diligence_package");
        assertTable(DueDiligenceReportEntity.class, "investment_due_diligence");
        assertTable(DueDiligenceItemEntity.class, "investment_due_diligence_item");
        assertThat(fieldNames(FeasibilityVersionEntity.class)).contains(
                "versionNo", "totalInvestment", "annualRevenue", "annualNetProfit", "roi", "irr");
        assertThat(fieldNames(DueDiligenceItemEntity.class)).contains(
                "severity", "blockingFlag", "problemDescription", "status");
    }

    @Test
    void schemeEntitiesShouldMapTheV240TablesAndBaseContract() {
        assertTable(InvestmentSchemeEntity.class, "investment_scheme");
        assertTable(InvestmentSchemeVersionEntity.class, "investment_scheme_version");
        assertTable(InvestmentSchemeFundingEntity.class, "investment_scheme_funding");
        assertThat(fieldNames(InvestmentSchemeVersionEntity.class)).contains(
                "versionNo", "totalAmount", "investmentMethod", "cooperationMode",
                "governanceArrangement", "exitPlan", "feasibilityVersionId",
                "dueDiligencePackageId");
        assertThat(fieldNames(InvestmentSchemeFundingEntity.class)).contains(
                "fundingType", "providerName", "amount", "costRate", "confirmedFlag");
    }

    private void assertTable(Class<?> entityType, String table) {
        assertThat(entityType.getAnnotation(TableName.class).value()).isEqualTo(table);
        assertBaseContract(entityType);
    }

    private void assertBaseContract(Class<?> entityType) {
        assertThat(BaseIdEntity.class).isAssignableFrom(entityType);
        assertThat(field("deleted").getAnnotation(TableLogic.class)).isNotNull();
        assertThat(field("version").getAnnotation(Version.class)).isNotNull();
        assertThat(fieldNames(BaseIdEntity.class)).contains("id", "deleteToken");
    }

    private Field field(String name) {
        try {
            return cn.gov.enterprise.common.persistence.BaseEntity.class.getDeclaredField(name);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(exception);
        }
    }

    private Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }
}
