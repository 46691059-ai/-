package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.investment.application.command.ConvertInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentProjectCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentOpportunityScopedLoader;
import cn.gov.enterprise.modules.investment.application.service.InvestmentOpportunityApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentProjectApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.project.application.service.ProjectApplicationService;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class InvestmentTransactionContractTest {

    @Test
    void createAndWorkflowUseCasesShouldUseRequiredReadWriteTransactions() throws Exception {
        assertTransaction(InvestmentProjectApplicationService.class.getMethod(
                "createInvestmentProject", CreateInvestmentProjectCommand.class), false);
        assertTransaction(InvestmentOpportunityApplicationService.class.getMethod(
                "createInvestmentOpportunity", CreateInvestmentOpportunityCommand.class), false);
        assertTransaction(InvestmentOpportunityApplicationService.class.getMethod(
                "convertOpportunity", Long.class, ConvertInvestmentOpportunityCommand.class), false);
        assertTransaction(cn.gov.enterprise.modules.investment.application.service
                .InvestmentSchemeApplicationService.class.getMethod(
                        "createVersion", Long.class,
                        cn.gov.enterprise.modules.investment.application.command
                                .CreateInvestmentSchemeVersionCommand.class), false);
    }

    @Test
    void queryUseCasesShouldUseReadOnlyTransactions() throws Exception {
        assertTransaction(InvestmentProjectApplicationService.class.getMethod(
                "queryInvestmentProject", Long.class), true);
        assertTransaction(InvestmentOpportunityApplicationService.class.getMethod(
                "queryInvestmentOpportunity", Long.class), true);
        assertTransaction(cn.gov.enterprise.modules.investment.application.service
                .InvestmentSchemeApplicationService.class.getMethod(
                        "queryVersions", Long.class), true);
    }

    @Test
    void conversionFailureShouldRollbackProjectLifecycleAndInvestmentWrites() {
        InvestmentOpportunityRepository opportunityRepository = org.mockito.Mockito.mock(
                InvestmentOpportunityRepository.class);
        InvestmentProjectRepository investmentRepository = org.mockito.Mockito.mock(
                InvestmentProjectRepository.class);
        InvestmentIdentityGenerator identityGenerator = org.mockito.Mockito.mock(
                InvestmentIdentityGenerator.class);
        ProjectAccessPolicy accessPolicy = org.mockito.Mockito.mock(ProjectAccessPolicy.class);
        CurrentSecurityContext securityContext = org.mockito.Mockito.mock(CurrentSecurityContext.class);
        InvestmentOpportunityScopedLoader loader = org.mockito.Mockito.mock(
                InvestmentOpportunityScopedLoader.class);
        ProjectApplicationService projectService = org.mockito.Mockito.mock(
                ProjectApplicationService.class);
        when(loader.lock(200L)).thenReturn(evaluatingOpportunity());
        when(projectService.createProject(any())).thenReturn(projectDetail(10L));
        when(identityGenerator.nextId()).thenReturn(100L);
        org.mockito.Mockito.doThrow(new IllegalStateException("investment persistence failure"))
                .when(investmentRepository).save(any(InvestmentProject.class));

        InvestmentOpportunityApplicationService target = new InvestmentOpportunityApplicationService(
                opportunityRepository, investmentRepository, identityGenerator, accessPolicy,
                securityContext, loader, projectService);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        InvestmentOpportunityApplicationService proxy =
                (InvestmentOpportunityApplicationService) proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.convertOpportunity(200L, conversionCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("investment persistence failure");

        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        org.mockito.Mockito.verify(opportunityRepository, org.mockito.Mockito.never())
                .updateState(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void feasibilityVersionFailureShouldTriggerTransactionRollback() {
        var repository = org.mockito.Mockito.mock(
                cn.gov.enterprise.modules.investment.domain.repository.FeasibilityRepository.class);
        var identity = org.mockito.Mockito.mock(InvestmentIdentityGenerator.class);
        var lifecycle = org.mockito.Mockito.mock(
                cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy.class);
        var header = new cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility(
                300L, 100L, null, null,
                cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility.Status.NOT_STARTED, 0);
        when(repository.findByInvestmentId(100L)).thenReturn(java.util.Optional.of(header));
        when(repository.findByIdForUpdate(300L)).thenReturn(java.util.Optional.of(header));
        when(repository.nextVersionNo(300L)).thenReturn(1);
        when(identity.nextId()).thenReturn(401L);
        org.mockito.Mockito.doThrow(new IllegalStateException("version persistence failure"))
                .when(repository).appendVersion(any());
        var target = new cn.gov.enterprise.modules.investment.application.service
                .InvestmentFeasibilityApplicationService(repository, identity, lifecycle);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        var proxy = (cn.gov.enterprise.modules.investment.application.service
                .InvestmentFeasibilityApplicationService) proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.createVersion(100L, feasibilityVersionCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    void schemeFundingPersistenceFailureShouldRollbackVersionAndHeaderWrites() {
        var repository = org.mockito.Mockito.mock(
                cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository.class);
        var identity = org.mockito.Mockito.mock(InvestmentIdentityGenerator.class);
        var lifecycle = org.mockito.Mockito.mock(
                cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy.class);
        var header = new cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme(
                300L, 100L, null, null,
                cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme.Status.NOT_STARTED, 0);
        when(repository.findFeasibilityReference(701L)).thenReturn(java.util.Optional.of(
                new cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository
                        .FeasibilityReference(100L,
                        cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion.Status.FROZEN,
                        cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion
                                .Conclusion.RECOMMENDED)));
        when(repository.findDueDiligenceReference(801L)).thenReturn(java.util.Optional.of(
                new cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository
                        .DueDiligenceReference(100L,
                        cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage.Status.FROZEN,
                        cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage.Conclusion.PASS,
                        0)));
        when(repository.findByInvestmentId(100L)).thenReturn(java.util.Optional.of(header));
        when(repository.findByIdForUpdate(300L)).thenReturn(java.util.Optional.of(header));
        when(repository.nextVersionNo(300L)).thenReturn(1);
        when(identity.nextId()).thenReturn(401L, 501L);
        org.mockito.Mockito.doThrow(new IllegalStateException("funding persistence failure"))
                .when(repository).appendVersion(any());
        var target = new cn.gov.enterprise.modules.investment.application.service
                .InvestmentSchemeApplicationService(repository, identity, lifecycle);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        var proxy = (cn.gov.enterprise.modules.investment.application.service
                .InvestmentSchemeApplicationService) proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.createVersion(100L, schemeVersionCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("funding persistence failure");
        assertThat(transactionManager.begun).isTrue();
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .moveCurrentVersion(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private void assertTransaction(Method method, boolean readOnly) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isEqualTo(readOnly);
    }

    private InvestmentOpportunity evaluatingOpportunity() {
        return new InvestmentOpportunity(
                200L, "OPP-TX-001", "事务测试机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, 40L, "新能源", null, new BigDecimal("300.00"),
                InvestmentOpportunity.Status.EVALUATING, "分析通过", null, null, null, 0);
    }

    private ConvertInvestmentOpportunityCommand conversionCommand() {
        return new ConvertInvestmentOpportunityCommand(
                "PRJ-TX-001", "INVESTMENT", LocalDate.now(), LocalDate.now().plusYears(1),
                "INV-TX-001", InvestmentProject.InvestmentType.EQUITY,
                InvestmentProject.InvestmentMethod.CASH, new BigDecimal("1000.00"),
                new BigDecimal("51.00"), new BigDecimal("200.00"), "MEDIUM", null);
    }

    private cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand
            feasibilityVersionCommand() {
        return new cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand(
                "FS-TX", "事务可研", cn.gov.enterprise.modules.investment.domain.model
                        .FeasibilityVersion.CompilerType.INTERNAL,
                20L, "数字产业部", LocalDate.now(), LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("300"), new BigDecimal("80"),
                new BigDecimal("20"), new BigDecimal("200"), null, null, null,
                cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion
                        .RiskConclusion.ACCEPTABLE,
                cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion
                        .Conclusion.RECOMMENDED,
                null, null);
    }

    private ProjectDtos.DetailResponse projectDetail(Long projectId) {
        ProjectDtos.Response response = new ProjectDtos.Response(
                projectId, "PRJ-TX-001", "事务测试机会", "01", "INVESTMENT",
                null, null, 40L, 20L, "RESERVE", null, null, null, null,
                new BigDecimal("1000.00"), null, new BigDecimal("300.00"),
                new BigDecimal("200.00"), null, null, null, "MEDIUM",
                BigDecimal.ZERO, null, null, null, null, 0);
        return new ProjectDtos.DetailResponse(response, List.of(), List.of(), 0, List.of(), 0);
    }

    private cn.gov.enterprise.modules.investment.application.command
            .CreateInvestmentSchemeVersionCommand schemeVersionCommand() {
        var funding = new cn.gov.enterprise.modules.investment.application.command
                .CreateSchemeFundingCommand("OWN_CAPITAL", "数投公司",
                new BigDecimal("1000"), BigDecimal.ZERO, LocalDate.now(), true, null);
        return new cn.gov.enterprise.modules.investment.application.command
                .CreateInvestmentSchemeVersionCommand(
                "SCHEME-TX", "事务投资方案", 20L, null,
                InvestmentProject.InvestmentType.EQUITY, new BigDecimal("1000"), "CNY",
                InvestmentProject.InvestmentMethod.CASH, null, BigDecimal.ZERO,
                new BigDecimal("51"), "COMMON", "CONTROL", null, "JOINT_VENTURE",
                null, null, null, null, null, null, null, 701L, 801L, null,
                List.of(funding));
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean begun;
        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
