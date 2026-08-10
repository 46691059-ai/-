package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.ConvertInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentProjectCommand;
import cn.gov.enterprise.modules.investment.application.command.ReviewInvestmentOpportunityCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentOpportunityScopedLoader;
import cn.gov.enterprise.modules.investment.application.service.InvestmentOpportunityApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentProjectApplicationService;
import cn.gov.enterprise.modules.investment.application.vo.OpportunityConversionResult;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.project.application.service.ProjectApplicationService;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class InvestmentApplicationServiceTest {
    @Mock InvestmentProjectRepository projectRepository;
    @Mock InvestmentOpportunityRepository opportunityRepository;
    @Mock InvestmentIdentityGenerator identityGenerator;
    @Mock ProjectAccessPolicy projectAccessPolicy;
    @Mock CurrentSecurityContext securityContext;
    @Mock InvestmentOpportunityScopedLoader scopedLoader;
    @Mock ProjectApplicationService projectApplicationService;

    private InvestmentProjectApplicationService projectService;
    private InvestmentOpportunityApplicationService opportunityService;

    @BeforeEach
    void setUp() {
        projectService = new InvestmentProjectApplicationService(
                projectRepository, identityGenerator, projectAccessPolicy);
        opportunityService = new InvestmentOpportunityApplicationService(
                opportunityRepository, projectRepository, identityGenerator,
                projectAccessPolicy, securityContext, scopedLoader, projectApplicationService);
    }

    @Test
    void shouldCreateInvestmentProjectAfterProjectAccessCheck() {
        when(projectAccessPolicy.requireAccessible(10L)).thenReturn(linkedProject());
        when(identityGenerator.nextId()).thenReturn(100L);
        CreateInvestmentProjectCommand command = new CreateInvestmentProjectCommand(
                10L, "INV-001", "储能投资", InvestmentProject.InvestmentType.EQUITY,
                InvestmentProject.InvestmentMethod.CASH, new BigDecimal("1000.00"),
                new BigDecimal("51.00"));

        InvestmentProject result = projectService.createInvestmentProject(command);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.responsibleOrgId()).isEqualTo(20L);
        verify(projectRepository).save(result);
    }

    @Test
    void shouldRejectLegacyUnknownMethodForNewInvestmentProject() {
        when(projectAccessPolicy.requireAccessible(10L)).thenReturn(linkedProject());
        CreateInvestmentProjectCommand command = new CreateInvestmentProjectCommand(
                10L, "INV-002", "未知出资", InvestmentProject.InvestmentType.OTHER,
                InvestmentProject.InvestmentMethod.LEGACY_UNKNOWN, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> projectService.createInvestmentProject(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史未知");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldCheckProjectPolicyBeforeLoadingProtectedInvestmentFacts() {
        when(projectRepository.findProjectIdById(100L)).thenReturn(Optional.of(10L));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(projectAccessPolicy).requireAccessible(10L);

        assertThatThrownBy(() -> projectService.queryInvestmentProject(100L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findById(100L);
    }

    @Test
    void shouldCreateOpportunityForCurrentUserAndAllowedOrganization() {
        when(identityGenerator.nextId()).thenReturn(200L);
        when(securityContext.userId()).thenReturn(40L);
        CreateInvestmentOpportunityCommand command = new CreateInvestmentOpportunityCommand(
                "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, "新能源", "合作方", new BigDecimal("300.00"));

        InvestmentOpportunity result = opportunityService.createInvestmentOpportunity(command);

        verify(projectAccessPolicy).requireOrgAccessible(20L);
        assertThat(result.ownerId()).isEqualTo(40L);
        assertThat(result.version()).isZero();
        verify(opportunityRepository).save(result);
    }

    @Test
    void shouldReviewOpportunityThroughLockedStateTransition() {
        InvestmentOpportunity current = opportunity(InvestmentOpportunity.Status.SCREENING);
        when(scopedLoader.lock(200L)).thenReturn(current);
        when(securityContext.username()).thenReturn("reviewer");
        when(opportunityRepository.updateState(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(true);

        InvestmentOpportunity changed = opportunityService.reviewOpportunity(
                200L, new ReviewInvestmentOpportunityCommand(
                        ReviewInvestmentOpportunityCommand.Decision.APPROVE, "初筛通过"));

        assertThat(changed.status()).isEqualTo(InvestmentOpportunity.Status.ANALYSING);
        assertThat(changed.version()).isEqualTo(1);
        verify(opportunityRepository).updateState(
                changed, InvestmentOpportunity.Status.SCREENING, 0, "reviewer");
    }

    @Test
    void shouldConvertOpportunityAndCreateAllAggregates() {
        InvestmentOpportunity current = opportunity(InvestmentOpportunity.Status.EVALUATING);
        when(scopedLoader.lock(200L)).thenReturn(current);
        when(projectApplicationService.createProject(any())).thenReturn(projectDetail(10L));
        when(identityGenerator.nextId()).thenReturn(100L);
        when(securityContext.username()).thenReturn("converter");
        when(opportunityRepository.updateState(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(true);

        OpportunityConversionResult result = opportunityService.convertOpportunity(
                200L, conversionCommand());

        assertThat(result.projectId()).isEqualTo(10L);
        assertThat(result.investmentProjectId()).isEqualTo(100L);
        assertThat(result.alreadyConverted()).isFalse();
        verify(projectApplicationService).createProject(any());
        verify(projectRepository).save(any(InvestmentProject.class));
        verify(opportunityRepository).updateState(
                any(), org.mockito.ArgumentMatchers.eq(InvestmentOpportunity.Status.EVALUATING),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq("converter"));
    }

    @Test
    void repeatedConversionShouldReturnExistingResultWithoutDuplicateCreation() {
        InvestmentOpportunity converted = convertedOpportunity();
        when(scopedLoader.lock(200L)).thenReturn(converted);

        OpportunityConversionResult result = opportunityService.convertOpportunity(
                200L, conversionCommand());

        assertThat(result.alreadyConverted()).isTrue();
        assertThat(result.projectId()).isEqualTo(10L);
        verify(projectApplicationService, never()).createProject(any());
        verify(projectRepository, never()).save(any());
        verify(opportunityRepository, never()).updateState(any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void conversionShouldFailWhenOptimisticStateUpdateLosesRace() {
        when(scopedLoader.lock(200L)).thenReturn(opportunity(InvestmentOpportunity.Status.EVALUATING));
        when(projectApplicationService.createProject(any())).thenReturn(projectDetail(10L));
        when(identityGenerator.nextId()).thenReturn(100L);
        when(securityContext.username()).thenReturn("converter");
        when(opportunityRepository.updateState(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> opportunityService.convertOpportunity(200L, conversionCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变化");
    }

    private InvestmentOpportunity opportunity(InvestmentOpportunity.Status status) {
        return new InvestmentOpportunity(
                200L, "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, 40L, "新能源", "合作方", new BigDecimal("300.00"), status,
                null, null, null, null, 0);
    }

    private InvestmentOpportunity convertedOpportunity() {
        return new InvestmentOpportunity(
                200L, "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, 40L, "新能源", "合作方", new BigDecimal("300.00"),
                InvestmentOpportunity.Status.CONVERTED, "评估通过", 10L, 100L,
                LocalDateTime.now(), 1);
    }

    private ConvertInvestmentOpportunityCommand conversionCommand() {
        return new ConvertInvestmentOpportunityCommand(
                "PRJ-001", "INVESTMENT", LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 8, 1), "INV-001",
                InvestmentProject.InvestmentType.EQUITY,
                InvestmentProject.InvestmentMethod.CASH,
                new BigDecimal("1000.00"), new BigDecimal("51.00"),
                new BigDecimal("200.00"), "MEDIUM", "机会转项目");
    }

    private ProjectDtos.DetailResponse projectDetail(Long projectId) {
        ProjectDtos.Response response = new ProjectDtos.Response(
                projectId, "PRJ-001", "储能机会", "01", "INVESTMENT",
                null, null, 40L, 20L, "RESERVE", null, null, null, null,
                new BigDecimal("1000.00"), null, new BigDecimal("300.00"),
                new BigDecimal("200.00"), null, null, null, "MEDIUM",
                BigDecimal.ZERO, null, null, null, null, 0);
        return new ProjectDtos.DetailResponse(response, List.of(), List.of(), 0, List.of(), 0);
    }

    private ProjectEntity linkedProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(10L);
        project.setProjectType("01");
        project.setDepartmentId(20L);
        project.setLeaderId(30L);
        return project;
    }
}
