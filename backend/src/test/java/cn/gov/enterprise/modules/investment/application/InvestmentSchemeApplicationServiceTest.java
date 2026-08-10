package cn.gov.enterprise.modules.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.application.command.CreateInvestmentSchemeVersionCommand;
import cn.gov.enterprise.modules.investment.application.command.CreateSchemeFundingCommand;
import cn.gov.enterprise.modules.investment.application.security.InvestmentLifecycleStagePolicy;
import cn.gov.enterprise.modules.investment.application.service.InvestmentSchemeApplicationService;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentSchemeVersion;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentSchemeApplicationServiceTest {
    @Mock InvestmentSchemeRepository repository;
    @Mock InvestmentIdentityGenerator identityGenerator;
    @Mock InvestmentLifecycleStagePolicy lifecyclePolicy;
    private InvestmentSchemeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentSchemeApplicationService(repository, identityGenerator, lifecyclePolicy);
    }

    @Test
    void newRevisionMustAppendVersionAndMultipleFundingSources() {
        prepareValidReferences();
        InvestmentScheme header = new InvestmentScheme(
                300L, 100L, 400L, null, InvestmentScheme.Status.DRAFT, 1);
        when(repository.findByInvestmentId(100L)).thenReturn(Optional.of(header));
        when(repository.findByIdForUpdate(300L)).thenReturn(Optional.of(header));
        when(repository.nextVersionNo(300L)).thenReturn(2);
        when(identityGenerator.nextId()).thenReturn(401L, 501L, 502L);
        when(repository.moveCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(true);

        InvestmentSchemeVersion result = service.createVersion(100L, validCommand());

        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.fundingSources()).hasSize(2);
        assertThat(result.fundingSources().get(0).ratio())
                .isEqualByComparingTo("60.0000");
        assertThat(result.fundingSources().get(1).ratio())
                .isEqualByComparingTo("40.0000");
        verify(repository).appendVersion(result);
        verify(repository).moveCurrentVersion(any(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void fundingTotalMustEqualSchemeTotal() {
        prepareValidReferences();
        InvestmentScheme header = new InvestmentScheme(
                300L, 100L, null, null, InvestmentScheme.Status.NOT_STARTED, 0);
        when(repository.findByInvestmentId(100L)).thenReturn(Optional.of(header));
        when(repository.findByIdForUpdate(300L)).thenReturn(Optional.of(header));
        when(repository.nextVersionNo(300L)).thenReturn(1);
        when(identityGenerator.nextId()).thenReturn(401L, 501L, 502L);

        CreateInvestmentSchemeVersionCommand invalid = commandWithFunding(List.of(
                funding("OWN_CAPITAL", "数投公司", "500"),
                funding("BANK_LOAN", "政策性银行", "400")));

        assertThatThrownBy(() -> service.createVersion(100L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Funding amount total");
        verify(repository, never()).appendVersion(any());
    }

    @Test
    void versionMustReferenceFrozenApprovedArgumentationOfSameInvestment() {
        when(repository.findFeasibilityReference(701L)).thenReturn(Optional.of(
                new InvestmentSchemeRepository.FeasibilityReference(
                        100L, FeasibilityVersion.Status.SUBMITTED,
                        FeasibilityVersion.Conclusion.RECOMMENDED)));

        assertThatThrownBy(() -> service.createVersion(100L, validCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已冻结");
        verify(repository, never()).appendVersion(any());
    }

    @Test
    void historicalSchemeVersionHasNoSupportedMutationUseCase() {
        assertThat(Arrays.stream(InvestmentSchemeRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.equals("updateVersion") || name.equals("deleteVersion"));
        assertThat(Arrays.stream(InvestmentSchemeApplicationService.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("updateVersion"));
    }

    private void prepareValidReferences() {
        when(repository.findFeasibilityReference(701L)).thenReturn(Optional.of(
                new InvestmentSchemeRepository.FeasibilityReference(
                        100L, FeasibilityVersion.Status.FROZEN,
                        FeasibilityVersion.Conclusion.RECOMMENDED)));
        when(repository.findDueDiligenceReference(801L)).thenReturn(Optional.of(
                new InvestmentSchemeRepository.DueDiligenceReference(
                        100L, DueDiligencePackage.Status.FROZEN,
                        DueDiligencePackage.Conclusion.PASS, 0)));
    }

    private CreateInvestmentSchemeVersionCommand validCommand() {
        return commandWithFunding(List.of(
                funding("OWN_CAPITAL", "数投公司", "600"),
                funding("BANK_LOAN", "政策性银行", "400")));
    }

    private CreateInvestmentSchemeVersionCommand commandWithFunding(
            List<CreateSchemeFundingCommand> funding) {
        return new CreateInvestmentSchemeVersionCommand(
                "SCHEME-001", "储能项目投资方案", 20L, null,
                InvestmentProject.InvestmentType.EQUITY, new BigDecimal("1000"), "CNY",
                InvestmentProject.InvestmentMethod.CASH, "分两期出资",
                BigDecimal.ZERO, new BigDecimal("51"), "COMMON", "CONTROL",
                "董事会席位安排", "JOINT_VENTURE", "合作方按比例出资",
                new BigDecimal("2000"), LocalDate.now(), "按持股比例分配",
                "EQUITY_TRANSFER", "五年后择机退出", "完成交割条件",
                701L, 801L, null, funding);
    }

    private CreateSchemeFundingCommand funding(String type, String provider, String amount) {
        return new CreateSchemeFundingCommand(type, provider, new BigDecimal(amount),
                BigDecimal.ZERO, LocalDate.now(), true, null);
    }
}
