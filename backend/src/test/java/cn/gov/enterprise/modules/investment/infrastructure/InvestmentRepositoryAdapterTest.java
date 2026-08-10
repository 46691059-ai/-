package cn.gov.enterprise.modules.investment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.InvestmentOpportunityRepositoryImpl;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.InvestmentProjectRepositoryImpl;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentOpportunityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentProjectEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentOpportunityMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentProjectMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.InvestmentOpportunityRow;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentRepositoryAdapterTest {
    @Mock InvestmentProjectMapper projectMapper;
    @Mock InvestmentOpportunityMapper opportunityMapper;
    @Mock ProjectMapper linkedProjectMapper;

    private InvestmentProjectRepositoryImpl projectRepository;
    private InvestmentOpportunityRepositoryImpl opportunityRepository;

    @BeforeEach
    void setUp() {
        projectRepository = new InvestmentProjectRepositoryImpl(projectMapper, linkedProjectMapper);
        opportunityRepository = new InvestmentOpportunityRepositoryImpl(opportunityMapper, projectMapper);
    }

    @Test
    void shouldRestoreInvestmentProjectWithLinkedProjectOwnership() {
        InvestmentProjectEntity entity = projectEntity();
        when(projectMapper.selectById(100L)).thenReturn(entity);
        when(linkedProjectMapper.selectById(10L)).thenReturn(linkedProject());

        InvestmentProject result = projectRepository.findById(100L).orElseThrow();

        assertThat(result.investmentNo()).isEqualTo("INV-001");
        assertThat(result.investmentMethod()).isEqualTo(InvestmentProject.InvestmentMethod.CASH);
        assertThat(result.responsibleOrgId()).isEqualTo(20L);
        assertThat(result.ownerId()).isEqualTo(30L);
    }

    @Test
    void shouldPersistInvestmentProjectThroughMapper() {
        InvestmentProject project = domainProject();
        when(linkedProjectMapper.selectById(10L)).thenReturn(linkedProject());
        when(projectMapper.insert(any(InvestmentProjectEntity.class))).thenReturn(1);

        projectRepository.save(project);

        ArgumentCaptor<InvestmentProjectEntity> captor =
                ArgumentCaptor.forClass(InvestmentProjectEntity.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getInvestmentMethod()).isEqualTo("CASH");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(captor.getValue().getDeleteToken()).isZero();
    }

    @Test
    void shouldRestoreOpportunityFromSingleScopedJoinRow() {
        InvestmentOpportunityRow row = new InvestmentOpportunityRow();
        row.setId(200L);
        row.setOpportunityNo("OPP-001");
        row.setOpportunityName("储能机会");
        row.setSourceType("GOVERNMENT");
        row.setProposingOrgId(20L);
        row.setProposerId(40L);
        row.setInvestmentDirection("新能源");
        row.setPreliminaryIncome(new BigDecimal("300.00"));
        row.setStatus("REGISTERED");
        row.setVersion(0);
        when(opportunityMapper.selectDomainRowById(200L)).thenReturn(row);

        InvestmentOpportunity result = opportunityRepository.findById(200L).orElseThrow();

        assertThat(result.opportunityNo()).isEqualTo("OPP-001");
        assertThat(result.ownerId()).isEqualTo(40L);
        assertThat(result.preliminaryReturn()).isEqualByComparingTo("300.00");
    }

    @Test
    void shouldPersistOpportunityThroughMapper() {
        InvestmentOpportunity opportunity = new InvestmentOpportunity(
                200L, "OPP-001", "储能机会", InvestmentOpportunity.Source.GOVERNMENT,
                20L, 40L, "新能源", "合作方", new BigDecimal("300.00"),
                InvestmentOpportunity.Status.REGISTERED,
                null, null, null, null, 0);
        when(opportunityMapper.insert(any(InvestmentOpportunityEntity.class))).thenReturn(1);

        opportunityRepository.save(opportunity);

        ArgumentCaptor<InvestmentOpportunityEntity> captor =
                ArgumentCaptor.forClass(InvestmentOpportunityEntity.class);
        verify(opportunityMapper).insert(captor.capture());
        assertThat(captor.getValue().getProposerId()).isEqualTo(40L);
        assertThat(captor.getValue().getSourceType()).isEqualTo("GOVERNMENT");
        assertThat(captor.getValue().getDeleteToken()).isZero();
    }

    @Test
    void shouldUseLockedReadAndOptimisticStateUpdate() {
        InvestmentOpportunityRow row = opportunityRow("EVALUATING", 3);
        when(opportunityMapper.selectDomainRowByIdForUpdate(200L)).thenReturn(row);
        when(opportunityMapper.updateState(
                200L, "CLOSED", "终止投资", null, null,
                "reviewer", "EVALUATING", 3)).thenReturn(1);

        InvestmentOpportunity current = opportunityRepository.findByIdForUpdate(200L).orElseThrow();
        InvestmentOpportunity closed = current.close("终止投资");

        assertThat(opportunityRepository.updateState(
                closed, current.status(), current.version(), "reviewer")).isTrue();
        verify(opportunityMapper).selectDomainRowByIdForUpdate(200L);
    }

    private InvestmentProject domainProject() {
        return new InvestmentProject(
                100L, 10L, "INV-001", "储能投资", InvestmentProject.InvestmentType.EQUITY,
                InvestmentProject.InvestmentMethod.CASH, new BigDecimal("1000.00"),
                new BigDecimal("51.00"), 20L, 30L, InvestmentProject.Status.DRAFT);
    }

    private InvestmentProjectEntity projectEntity() {
        InvestmentProjectEntity entity = new InvestmentProjectEntity();
        entity.setId(100L);
        entity.setProjectId(10L);
        entity.setInvestmentNo("INV-001");
        entity.setInvestmentName("储能投资");
        entity.setInvestmentType("EQUITY");
        entity.setInvestmentMethod("CASH");
        entity.setTotalAmount(new BigDecimal("1000.00"));
        entity.setInvestmentRatio(new BigDecimal("51.00"));
        entity.setStatus("DRAFT");
        return entity;
    }

    private ProjectEntity linkedProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(10L);
        project.setProjectType("01");
        project.setDepartmentId(20L);
        project.setLeaderId(30L);
        return project;
    }

    private InvestmentOpportunityRow opportunityRow(String status, int version) {
        InvestmentOpportunityRow row = new InvestmentOpportunityRow();
        row.setId(200L);
        row.setOpportunityNo("OPP-001");
        row.setOpportunityName("储能机会");
        row.setSourceType("GOVERNMENT");
        row.setProposingOrgId(20L);
        row.setProposerId(40L);
        row.setInvestmentDirection("新能源");
        row.setPreliminaryIncome(new BigDecimal("300.00"));
        row.setStatus(status);
        row.setVersion(version);
        return row;
    }
}
