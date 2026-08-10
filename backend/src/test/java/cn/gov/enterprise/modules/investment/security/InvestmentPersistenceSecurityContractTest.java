package cn.gov.enterprise.modules.investment.security;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.common.datascope.annotation.DataScope;
import cn.gov.enterprise.modules.investment.application.command.ConvertInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceReportCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentSchemeVersionCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentOpportunityScopedLoader;
import cn.gov.enterprise.modules.investment.application.service.InvestmentFeasibilityApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDueDiligenceApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentOpportunityApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentSchemeApplicationService;
import cn.gov.enterprise.modules.investment.controller.InvestmentSchemeController;
import cn.gov.enterprise.modules.investment.controller.InvestmentOpportunityController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class InvestmentPersistenceSecurityContractTest {

    @Test
    void workflowUseCasesShouldDeclareDedicatedRbacAuthorities() throws Exception {
        assertPermission(InvestmentOpportunityApplicationService.class.getMethod(
                        "createInvestmentOpportunity", CreateInvestmentOpportunityCommand.class),
                "hasAuthority('investment:opportunity:create')");
        assertPermission(InvestmentOpportunityApplicationService.class.getMethod(
                        "submitOpportunity", Long.class),
                "hasAuthority('investment:opportunity:create')");
        assertPermission(InvestmentOpportunityApplicationService.class.getMethod(
                        "reviewOpportunity", Long.class, ReviewInvestmentOpportunityCommand.class),
                "hasAuthority('investment:opportunity:review')");
        assertPermission(InvestmentOpportunityApplicationService.class.getMethod(
                        "convertOpportunity", Long.class, ConvertInvestmentOpportunityCommand.class),
                "hasAuthority('investment:opportunity:convert')");
    }

    @Test
    void scopedLoaderShouldEnforceOpportunityOrganizationAndCreatorScope() throws Exception {
        assertDataScope(InvestmentOpportunityScopedLoader.class.getMethod("load", Long.class));
        assertDataScope(InvestmentOpportunityScopedLoader.class.getMethod("lock", Long.class));
    }

    @Test
    void controllerMustNotDependOnRepositoryOrMapperForStateMutation() {
        assertThat(InvestmentOpportunityController.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType())
                        .isEqualTo(InvestmentOpportunityApplicationService.class));
    }

    @Test
    void argumentationUseCasesShouldUseDedicatedPermissions() throws Exception {
        assertPermission(InvestmentFeasibilityApplicationService.class.getMethod(
                        "createVersion", Long.class, CreateFeasibilityVersionCommand.class),
                "hasAuthority('investment:feasibility:edit')");
        assertPermission(InvestmentFeasibilityApplicationService.class.getMethod(
                        "queryVersions", Long.class),
                "hasAuthority('investment:feasibility:view')");
        assertPermission(InvestmentDueDiligenceApplicationService.class.getMethod(
                        "createReport", Long.class, CreateDueDiligenceReportCommand.class),
                "hasAuthority('investment:due_diligence:edit')");
        assertPermission(InvestmentDueDiligenceApplicationService.class.getMethod(
                        "queryReports", Long.class),
                "hasAuthority('investment:due_diligence:view')");
        assertPermission(InvestmentSchemeApplicationService.class.getMethod(
                        "createVersion", Long.class, CreateInvestmentSchemeVersionCommand.class),
                "hasAuthority('investment:scheme:edit')");
        assertPermission(InvestmentSchemeApplicationService.class.getMethod(
                        "queryVersions", Long.class),
                "hasAuthority('investment:scheme:view')");
    }

    @Test
    void schemeControllerMustOnlyDependOnItsApplicationService() {
        assertThat(InvestmentSchemeController.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType())
                        .isEqualTo(InvestmentSchemeApplicationService.class));
    }

    private void assertPermission(Method method, String expression) {
        assertThat(method.getAnnotation(PreAuthorize.class))
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo(expression);
    }

    private void assertDataScope(Method method) {
        DataScope dataScope = method.getAnnotation(DataScope.class);
        assertThat(dataScope).isNotNull();
        assertThat(dataScope.orgField()).isEqualTo("io.proposing_org_id");
        assertThat(dataScope.userField()).isEqualTo("io.create_by");
    }
}
