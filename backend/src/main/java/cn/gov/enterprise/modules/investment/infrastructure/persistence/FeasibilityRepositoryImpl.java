package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentFeasibility;
import cn.gov.enterprise.modules.investment.domain.repository.FeasibilityRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.FeasibilityVersionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentFeasibilityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.FeasibilityVersionMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentFeasibilityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class FeasibilityRepositoryImpl implements FeasibilityRepository {
    private final InvestmentFeasibilityMapper headerMapper;
    private final FeasibilityVersionMapper versionMapper;

    public FeasibilityRepositoryImpl(
            InvestmentFeasibilityMapper headerMapper, FeasibilityVersionMapper versionMapper) {
        this.headerMapper = headerMapper;
        this.versionMapper = versionMapper;
    }

    @Override
    public Optional<InvestmentFeasibility> findByInvestmentId(Long investmentId) {
        InvestmentFeasibilityEntity entity = headerMapper.selectOne(
                new LambdaQueryWrapper<InvestmentFeasibilityEntity>()
                        .eq(InvestmentFeasibilityEntity::getInvestmentId, investmentId));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<InvestmentFeasibility> findByIdForUpdate(Long feasibilityId) {
        return Optional.ofNullable(headerMapper.selectByIdForUpdate(feasibilityId)).map(this::toDomain);
    }

    @Override
    public List<FeasibilityVersion> findVersions(Long feasibilityId) {
        return versionMapper.selectList(new LambdaQueryWrapper<FeasibilityVersionEntity>()
                        .eq(FeasibilityVersionEntity::getFeasibilityId, feasibilityId)
                        .orderByDesc(FeasibilityVersionEntity::getVersionNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public int nextVersionNo(Long feasibilityId) {
        return versionMapper.selectMaxVersionNo(feasibilityId) + 1;
    }

    @Override
    public void saveArchive(InvestmentFeasibility feasibility) {
        InvestmentFeasibilityEntity entity = new InvestmentFeasibilityEntity();
        entity.setId(feasibility.id());
        entity.setInvestmentId(feasibility.investmentProjectId());
        entity.setCurrentVersionId(feasibility.currentVersionId());
        entity.setCurrentFrozenVersionId(feasibility.currentFrozenVersionId());
        entity.setStatus(feasibility.status().name());
        entity.setAnnualIncome(BigDecimal.ZERO);
        entity.setAnnualCost(BigDecimal.ZERO);
        entity.setAnnualProfit(BigDecimal.ZERO);
        entity.setDeleteToken(0L);
        entity.setVersion(feasibility.version());
        try {
            if (headerMapper.insert(entity) != 1) throw persistenceFailure("可研档案保存失败");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0649", "该投资事项已存在可研档案");
        }
    }

    @Override
    public void appendVersion(FeasibilityVersion version) {
        try {
            if (versionMapper.insert(toEntity(version)) != 1) {
                throw persistenceFailure("可研版本保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0649", "可研版本号冲突，请重试");
        }
    }

    @Override
    public boolean moveCurrentVersion(InvestmentFeasibility changed, int expectedVersion) {
        return headerMapper.update(null, new LambdaUpdateWrapper<InvestmentFeasibilityEntity>()
                .eq(InvestmentFeasibilityEntity::getId, changed.id())
                .eq(InvestmentFeasibilityEntity::getVersion, expectedVersion)
                .set(InvestmentFeasibilityEntity::getCurrentVersionId, changed.currentVersionId())
                .set(InvestmentFeasibilityEntity::getStatus, changed.status().name())
                .set(InvestmentFeasibilityEntity::getVersion, changed.version())) == 1;
    }

    private InvestmentFeasibility toDomain(InvestmentFeasibilityEntity entity) {
        return new InvestmentFeasibility(
                entity.getId(), entity.getInvestmentId(), entity.getCurrentVersionId(),
                entity.getCurrentFrozenVersionId(),
                InvestmentFeasibility.Status.valueOf(entity.getStatus()), entity.getVersion());
    }

    private FeasibilityVersion toDomain(FeasibilityVersionEntity entity) {
        return new FeasibilityVersion(
                entity.getId(), entity.getFeasibilityId(), entity.getVersionNo(),
                entity.getReportNo(), entity.getReportName(),
                FeasibilityVersion.CompilerType.valueOf(entity.getCompilerType()),
                entity.getCompilerOrgId(), entity.getCompilerOrgName(), entity.getPreparedDate(),
                entity.getBaseDate(), entity.getTotalInvestment(), entity.getAnnualRevenue(),
                entity.getAnnualCost(), entity.getAnnualTax(), entity.getAnnualNetProfit(),
                entity.getRoi(), entity.getIrr(), entity.getPaybackPeriod(),
                FeasibilityVersion.RiskConclusion.valueOf(entity.getRiskConclusion()),
                FeasibilityVersion.Conclusion.valueOf(entity.getConclusion()),
                entity.getConclusionSummary(), entity.getPrimaryFileId(),
                FeasibilityVersion.Status.valueOf(entity.getStatus()), entity.getVersion());
    }

    private FeasibilityVersionEntity toEntity(FeasibilityVersion version) {
        FeasibilityVersionEntity entity = new FeasibilityVersionEntity();
        entity.setId(version.id());
        entity.setFeasibilityId(version.feasibilityId());
        entity.setVersionNo(version.versionNo());
        entity.setReportNo(version.reportNo());
        entity.setReportName(version.reportName());
        entity.setCompilerType(version.compilerType().name());
        entity.setCompilerOrgId(version.compilerOrgId());
        entity.setCompilerOrgName(version.compilerOrgName());
        entity.setPreparedDate(version.preparedDate());
        entity.setBaseDate(version.baseDate());
        entity.setCurrencyCode("CNY");
        entity.setTotalInvestment(version.totalInvestment());
        entity.setOwnCapital(BigDecimal.ZERO);
        entity.setFinancingAmount(BigDecimal.ZERO);
        entity.setAnnualRevenue(version.annualRevenue());
        entity.setAnnualCost(version.annualCost());
        entity.setAnnualTax(version.annualTax());
        entity.setAnnualNetProfit(version.annualNetProfit());
        entity.setRoi(version.roi());
        entity.setIrr(version.irr());
        entity.setPaybackPeriod(version.paybackPeriod());
        entity.setRiskConclusion(version.riskConclusion().name());
        entity.setConclusion(version.conclusion().name());
        entity.setConclusionSummary(version.conclusionSummary());
        entity.setPrimaryFileId(version.primaryFileId());
        entity.setStatus(version.status().name());
        entity.setDeleteToken(0L);
        entity.setVersion(version.version());
        return entity;
    }

    private BusinessException persistenceFailure(String message) {
        return new BusinessException("B0640", message);
    }
}
