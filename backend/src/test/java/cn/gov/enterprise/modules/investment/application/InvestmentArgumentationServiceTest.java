package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateDueDiligenceReportCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateFeasibilityVersionCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.application.service.InvestmentDueDiligenceApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentFeasibilityApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceType;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import cn.gov.enterprise.modules.investment.domain.repository.DueDiligenceRepository;
import cn.gov.enterprise.modules.investment.domain.repository.FeasibilityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class InvestmentArgumentationServiceTest {
    @Mock FeasibilityRepository feasibilityRepository;
    @Mock DueDiligenceRepository dueDiligenceRepository;
    @Mock InvestmentIdentityGenerator identityGenerator;
    @Mock InvestmentLifecycleStagePolicy lifecyclePolicy;
    private InvestmentFeasibilityApplicationService feasibilityService;
    private InvestmentDueDiligenceApplicationService dueDiligenceService;

    @BeforeEach
    void setUp() {
        feasibilityService = new InvestmentFeasibilityApplicationService(
                feasibilityRepository, identityGenerator, lifecyclePolicy);
        dueDiligenceService = new InvestmentDueDiligenceApplicationService(
                dueDiligenceRepository, identityGenerator, lifecyclePolicy);
    }

    @Test
    void newFeasibilityRevisionMustAppendANewVersion() {
        InvestmentFeasibility header = new InvestmentFeasibility(
                300L, 100L, 400L, null, InvestmentFeasibility.Status.IN_PROGRESS, 1);
        when(feasibilityRepository.findByInvestmentId(100L)).thenReturn(Optional.of(header));
        when(feasibilityRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(header));
        when(feasibilityRepository.nextVersionNo(300L)).thenReturn(2);
        when(identityGenerator.nextId()).thenReturn(401L);
        when(feasibilityRepository.moveCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(1)))
                .thenReturn(true);

        FeasibilityVersion version = feasibilityService.createVersion(100L, versionCommand());

        assertThat(version.id()).isEqualTo(401L);
        assertThat(version.versionNo()).isEqualTo(2);
        verify(feasibilityRepository).appendVersion(version);
        verify(feasibilityRepository).moveCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void failedHeaderCasMustRejectTheWholeVersionTransaction() {
        InvestmentFeasibility header = new InvestmentFeasibility(
                300L, 100L, null, null, InvestmentFeasibility.Status.NOT_STARTED, 0);
        when(feasibilityRepository.findByInvestmentId(100L)).thenReturn(Optional.of(header));
        when(feasibilityRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(header));
        when(feasibilityRepository.nextVersionNo(300L)).thenReturn(1);
        when(identityGenerator.nextId()).thenReturn(401L);
        when(feasibilityRepository.moveCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(false);

        assertThatThrownBy(() -> feasibilityService.createVersion(100L, versionCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发修改");
        verify(feasibilityRepository).appendVersion(any());
    }

    @Test
    void historicalFeasibilityVersionHasNoSupportedMutationUseCase() {
        assertThat(Arrays.stream(FeasibilityRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.equals("updateVersion") || name.equals("deleteVersion"));
        assertThat(Arrays.stream(InvestmentFeasibilityApplicationService.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("updateVersion"));
    }

    @Test
    void shouldSupportAllFourDueDiligenceReportTypes() {
        DueDiligencePackage pkg = new DueDiligencePackage(
                500L, 100L, 1, "RULE-1", Set.of(DueDiligenceType.values()),
                DueDiligencePackage.Conclusion.PENDING, 0,
                DueDiligencePackage.Status.PLANNED, 0);
        when(dueDiligenceRepository.findPackage(500L)).thenReturn(Optional.of(pkg));
        when(dueDiligenceRepository.findInvestmentIdByPackageId(500L))
                .thenReturn(Optional.of(100L));
        when(dueDiligenceRepository.nextReportVersion(
                org.mockito.ArgumentMatchers.eq(500L), any())).thenReturn(1);
        when(identityGenerator.nextId()).thenReturn(601L, 602L, 603L, 604L);

        for (DueDiligenceType type : DueDiligenceType.values()) {
            DueDiligenceReport report = dueDiligenceService.createReport(
                    500L, reportCommand(type));
            assertThat(report.type()).isEqualTo(type);
            assertThat(report.reportVersion()).isEqualTo(1);
        }
        verify(dueDiligenceRepository, org.mockito.Mockito.times(4)).appendReport(any());
    }

    @Test
    void writeUseCasesMustBeTransactional() throws Exception {
        assertThat(InvestmentFeasibilityApplicationService.class
                .getMethod("createVersion", Long.class, CreateFeasibilityVersionCommand.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(InvestmentDueDiligenceApplicationService.class
                .getMethod("createReport", Long.class, CreateDueDiligenceReportCommand.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private CreateFeasibilityVersionCommand versionCommand() {
        return new CreateFeasibilityVersionCommand(
                "FS-001", "储能可研报告", FeasibilityVersion.CompilerType.INTERNAL,
                20L, "数字产业部", LocalDate.now(), LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("300"), new BigDecimal("80"),
                new BigDecimal("20"), new BigDecimal("200"), new BigDecimal("20"),
                new BigDecimal("18"), new BigDecimal("5"),
                FeasibilityVersion.RiskConclusion.ACCEPTABLE,
                FeasibilityVersion.Conclusion.RECOMMENDED, "建议实施", null);
    }

    private CreateDueDiligenceReportCommand reportCommand(DueDiligenceType type) {
        return new CreateDueDiligenceReportCommand(
                type, "DD-" + type.name(), type.name() + "尽调报告", "受托机构", 40L,
                LocalDate.now(), LocalDate.now(), LocalDate.now(),
                DueDiligenceReport.Conclusion.PENDING, null, null);
    }
}
