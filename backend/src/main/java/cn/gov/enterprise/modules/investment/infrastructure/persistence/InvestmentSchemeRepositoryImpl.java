package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.FeasibilityVersion;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentScheme;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentSchemeVersion;
import cn.gov.enterprise.modules.investment.domain.model.SchemeFunding;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentSchemeRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeFundingEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeVersionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentSchemeFundingMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentSchemeMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentSchemeVersionMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.DueDiligenceReferenceRow;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.FeasibilityReferenceRow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class InvestmentSchemeRepositoryImpl implements InvestmentSchemeRepository {
    private final InvestmentSchemeMapper schemeMapper;
    private final InvestmentSchemeVersionMapper versionMapper;
    private final InvestmentSchemeFundingMapper fundingMapper;

    public InvestmentSchemeRepositoryImpl(
            InvestmentSchemeMapper schemeMapper,
            InvestmentSchemeVersionMapper versionMapper,
            InvestmentSchemeFundingMapper fundingMapper) {
        this.schemeMapper = schemeMapper;
        this.versionMapper = versionMapper;
        this.fundingMapper = fundingMapper;
    }

    @Override
    public Optional<InvestmentScheme> findByInvestmentId(Long investmentId) {
        InvestmentSchemeEntity entity = schemeMapper.selectOne(
                new LambdaQueryWrapper<InvestmentSchemeEntity>()
                        .eq(InvestmentSchemeEntity::getInvestmentId, investmentId));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<InvestmentScheme> findByIdForUpdate(Long schemeId) {
        return Optional.ofNullable(schemeMapper.selectByIdForUpdate(schemeId)).map(this::toDomain);
    }

    @Override
    public List<InvestmentSchemeVersion> findVersions(Long schemeId) {
        List<InvestmentSchemeVersionEntity> versions = versionMapper.selectList(
                new LambdaQueryWrapper<InvestmentSchemeVersionEntity>()
                        .eq(InvestmentSchemeVersionEntity::getSchemeId, schemeId)
                        .orderByDesc(InvestmentSchemeVersionEntity::getVersionNo));
        if (versions.isEmpty()) return List.of();
        List<Long> versionIds = versions.stream().map(InvestmentSchemeVersionEntity::getId).toList();
        Map<Long, List<InvestmentSchemeFundingEntity>> funding = fundingMapper.selectList(
                        new LambdaQueryWrapper<InvestmentSchemeFundingEntity>()
                                .in(InvestmentSchemeFundingEntity::getSchemeVersionId, versionIds)
                                .orderByAsc(InvestmentSchemeFundingEntity::getId))
                .stream().collect(Collectors.groupingBy(InvestmentSchemeFundingEntity::getSchemeVersionId));
        return versions.stream()
                .map(value -> toDomain(value, funding.getOrDefault(value.getId(), Collections.emptyList())))
                .toList();
    }

    @Override
    public int nextVersionNo(Long schemeId) {
        return versionMapper.selectMaxVersionNo(schemeId) + 1;
    }

    @Override
    public void saveArchive(InvestmentScheme scheme) {
        InvestmentSchemeEntity entity = new InvestmentSchemeEntity();
        entity.setId(scheme.id());
        entity.setInvestmentId(scheme.investmentProjectId());
        entity.setCurrentVersionId(scheme.currentVersionId());
        entity.setCurrentFrozenVersionId(scheme.currentFrozenVersionId());
        entity.setStatus(scheme.status().name());
        entity.setDeleteToken(0L);
        entity.setVersion(scheme.version());
        try {
            if (schemeMapper.insert(entity) != 1) throw persistenceFailure("投资方案档案保存失败");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0679", "该投资事项已存在投资方案档案");
        }
    }

    @Override
    public void appendVersion(InvestmentSchemeVersion version) {
        try {
            if (versionMapper.insert(toEntity(version)) != 1) {
                throw persistenceFailure("投资方案版本保存失败");
            }
            for (SchemeFunding funding : version.fundingSources()) {
                if (fundingMapper.insert(toEntity(funding)) != 1) {
                    throw persistenceFailure("投资方案资金来源保存失败");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0679", "投资方案版本或资金来源重复，请重试");
        }
    }

    @Override
    public boolean moveCurrentVersion(InvestmentScheme changed, int expectedVersion) {
        return schemeMapper.update(null, new LambdaUpdateWrapper<InvestmentSchemeEntity>()
                .eq(InvestmentSchemeEntity::getId, changed.id())
                .eq(InvestmentSchemeEntity::getVersion, expectedVersion)
                .set(InvestmentSchemeEntity::getCurrentVersionId, changed.currentVersionId())
                .set(InvestmentSchemeEntity::getStatus, changed.status().name())
                .set(InvestmentSchemeEntity::getVersion, changed.version())) == 1;
    }

    @Override
    public Optional<FeasibilityReference> findFeasibilityReference(Long feasibilityVersionId) {
        FeasibilityReferenceRow row = schemeMapper.selectFeasibilityReference(feasibilityVersionId);
        if (row == null) return Optional.empty();
        try {
            return Optional.of(new FeasibilityReference(row.investmentId(),
                    FeasibilityVersion.Status.valueOf(row.status()),
                    FeasibilityVersion.Conclusion.valueOf(row.conclusion())));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("B0670", "可研版本状态数据无效");
        }
    }

    @Override
    public Optional<DueDiligenceReference> findDueDiligenceReference(Long packageId) {
        DueDiligenceReferenceRow row = schemeMapper.selectDueDiligenceReference(packageId);
        if (row == null) return Optional.empty();
        try {
            return Optional.of(new DueDiligenceReference(row.investmentId(),
                    DueDiligencePackage.Status.valueOf(row.status()),
                    DueDiligencePackage.Conclusion.valueOf(row.overallConclusion()),
                    row.openBlockingCount() == null ? 0 : row.openBlockingCount()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("B0670", "尽调包状态数据无效");
        }
    }

    private InvestmentScheme toDomain(InvestmentSchemeEntity entity) {
        return new InvestmentScheme(entity.getId(), entity.getInvestmentId(),
                entity.getCurrentVersionId(), entity.getCurrentFrozenVersionId(),
                InvestmentScheme.Status.valueOf(entity.getStatus()), valueOrZero(entity.getVersion()));
    }

    private InvestmentSchemeVersion toDomain(
            InvestmentSchemeVersionEntity entity, List<InvestmentSchemeFundingEntity> fundingEntities) {
        List<SchemeFunding> funding = fundingEntities.stream()
                .map(value -> toDomain(value, entity.getTotalAmount())).toList();
        return new InvestmentSchemeVersion(
                entity.getId(), entity.getSchemeId(), entity.getVersionNo(), entity.getSchemeNo(),
                entity.getSchemeName(), entity.getInvestmentSubjectOrgId(), entity.getInvesteeCompanyId(),
                InvestmentProject.InvestmentType.valueOf(entity.getInvestmentType()),
                entity.getTotalAmount(), entity.getCurrencyCode(),
                InvestmentProject.InvestmentMethod.valueOf(entity.getInvestmentMethod()),
                entity.getContributionScheduleSummary(), entity.getPreInvestmentRatio(),
                entity.getPostInvestmentRatio(), entity.getShareType(), entity.getControlType(),
                entity.getGovernanceArrangement(), entity.getCooperationMode(),
                entity.getPartnerArrangement(), entity.getValuationAmount(), entity.getValuationBaseDate(),
                entity.getIncomeDistribution(), entity.getExitType(), entity.getExitPlan(),
                entity.getConditionsPrecedent(), entity.getFeasibilityVersionId(),
                entity.getDueDiligencePackageId(), entity.getPrimaryFileId(),
                InvestmentSchemeVersion.Status.valueOf(entity.getStatus()), funding,
                valueOrZero(entity.getVersion()));
    }

    private SchemeFunding toDomain(
            InvestmentSchemeFundingEntity entity, java.math.BigDecimal totalAmount) {
        return new SchemeFunding(entity.getId(), entity.getSchemeVersionId(), entity.getFundingType(),
                entity.getProviderName(), entity.getAmount(),
                SchemeFunding.calculateRatio(entity.getAmount(), totalAmount),
                entity.getCostRate(), entity.getAvailableDate(),
                Integer.valueOf(1).equals(entity.getConfirmedFlag()), entity.getEvidenceFileId(),
                valueOrZero(entity.getVersion()));
    }

    private InvestmentSchemeVersionEntity toEntity(InvestmentSchemeVersion value) {
        InvestmentSchemeVersionEntity entity = new InvestmentSchemeVersionEntity();
        entity.setId(value.id());
        entity.setSchemeId(value.schemeId());
        entity.setVersionNo(value.versionNo());
        entity.setSchemeNo(value.schemeNo());
        entity.setSchemeName(value.schemeName());
        entity.setInvestmentSubjectOrgId(value.investmentSubjectOrgId());
        entity.setInvesteeCompanyId(value.investeeCompanyId());
        entity.setInvestmentType(value.investmentType().name());
        entity.setTotalAmount(value.totalAmount());
        entity.setCurrencyCode(value.currencyCode());
        entity.setInvestmentMethod(value.investmentMethod().name());
        entity.setContributionScheduleSummary(value.contributionScheduleSummary());
        entity.setPreInvestmentRatio(value.preInvestmentRatio());
        entity.setPostInvestmentRatio(value.postInvestmentRatio());
        entity.setShareType(value.shareType());
        entity.setControlType(value.controlType());
        entity.setGovernanceArrangement(value.governanceArrangement());
        entity.setCooperationMode(value.cooperationMode());
        entity.setPartnerArrangement(value.partnerArrangement());
        entity.setValuationAmount(value.valuationAmount());
        entity.setValuationBaseDate(value.valuationBaseDate());
        entity.setIncomeDistribution(value.incomeDistribution());
        entity.setExitType(value.exitType());
        entity.setExitPlan(value.exitPlan());
        entity.setConditionsPrecedent(value.conditionsPrecedent());
        entity.setFeasibilityVersionId(value.feasibilityVersionId());
        entity.setDueDiligencePackageId(value.dueDiligencePackageId());
        entity.setPrimaryFileId(value.primaryFileId());
        entity.setStatus(value.status().name());
        entity.setDeleteToken(0L);
        entity.setVersion(value.version());
        return entity;
    }

    private InvestmentSchemeFundingEntity toEntity(SchemeFunding value) {
        InvestmentSchemeFundingEntity entity = new InvestmentSchemeFundingEntity();
        entity.setId(value.id());
        entity.setSchemeVersionId(value.schemeVersionId());
        entity.setFundingType(value.fundingType());
        entity.setProviderName(value.providerName());
        entity.setAmount(value.amount());
        entity.setCostRate(value.costRate());
        entity.setAvailableDate(value.availableDate());
        entity.setConfirmedFlag(value.confirmed() ? 1 : 0);
        entity.setEvidenceFileId(value.evidenceFileId());
        entity.setDeleteToken(0L);
        entity.setVersion(value.version());
        return entity;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BusinessException persistenceFailure(String message) {
        return new BusinessException("B0670", message);
    }
}
