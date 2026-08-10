package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentOpportunity;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentOpportunityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentProjectEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentOpportunityMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentProjectMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.InvestmentOpportunityRow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis Plus persistence adapter for InvestmentOpportunity. */
@Repository
public class InvestmentOpportunityRepositoryImpl implements InvestmentOpportunityRepository {
    private final InvestmentOpportunityMapper opportunityMapper;
    private final InvestmentProjectMapper investmentProjectMapper;

    public InvestmentOpportunityRepositoryImpl(
            InvestmentOpportunityMapper opportunityMapper,
            InvestmentProjectMapper investmentProjectMapper) {
        this.opportunityMapper = opportunityMapper;
        this.investmentProjectMapper = investmentProjectMapper;
    }

    @Override
    public Optional<InvestmentOpportunity> findById(Long opportunityId) {
        InvestmentOpportunityRow row = opportunityMapper.selectDomainRowById(opportunityId);
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public Optional<InvestmentOpportunity> findByIdForUpdate(Long opportunityId) {
        InvestmentOpportunityRow row = opportunityMapper.selectDomainRowByIdForUpdate(opportunityId);
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public boolean existsByOpportunityNo(String opportunityNo) {
        return opportunityMapper.selectCount(
                new LambdaQueryWrapper<InvestmentOpportunityEntity>()
                        .eq(InvestmentOpportunityEntity::getOpportunityNo, opportunityNo)) > 0;
    }

    @Override
    public void save(InvestmentOpportunity opportunity) {
        validateConversion(opportunity);
        try {
            if (opportunityMapper.insert(toEntity(opportunity)) != 1) {
                throw new BusinessException("B0610", "投资机会保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0619", "投资机会编号已存在");
        }
    }

    @Override
    public boolean updateState(
            InvestmentOpportunity opportunity,
            InvestmentOpportunity.Status expectedStatus,
            int expectedVersion,
            String operator) {
        validateConversion(opportunity);
        return opportunityMapper.updateState(
                opportunity.id(), opportunity.status().name(), opportunity.reviewConclusion(),
                opportunity.convertedInvestmentProjectId(), opportunity.convertedAt(), operator,
                expectedStatus.name(), expectedVersion) == 1;
    }

    private InvestmentOpportunity toDomain(InvestmentOpportunityRow entity) {
        if (entity.getInvestmentId() != null && entity.getConvertedProjectId() == null) {
                throw new BusinessException("B0610", "投资机会转化关系不完整");
        }
        try {
            return new InvestmentOpportunity(
                    entity.getId(), entity.getOpportunityNo(), entity.getOpportunityName(),
                    InvestmentOpportunity.Source.valueOf(entity.getSourceType()),
                    entity.getProposingOrgId(), entity.getProposerId(),
                    entity.getInvestmentDirection(), entity.getPartnerSummary(),
                    valueOrZero(entity.getPreliminaryIncome()),
                    InvestmentOpportunity.Status.valueOf(entity.getStatus()),
                    entity.getScreeningConclusion(), entity.getConvertedProjectId(),
                    entity.getInvestmentId(), entity.getConvertedTime(), entity.getVersion());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("B0610", "投资机会数据不符合当前领域模型");
        }
    }

    private InvestmentOpportunityEntity toEntity(InvestmentOpportunity opportunity) {
        InvestmentOpportunityEntity entity = new InvestmentOpportunityEntity();
        entity.setId(opportunity.id());
        entity.setOpportunityNo(opportunity.opportunityNo());
        entity.setInvestmentId(opportunity.convertedInvestmentProjectId());
        entity.setOpportunityName(opportunity.opportunityName());
        entity.setSourceType(opportunity.source().name());
        entity.setProposingOrgId(opportunity.proposingOrgId());
        entity.setProposerId(opportunity.ownerId());
        entity.setInvestmentDirection(opportunity.investmentDirection());
        entity.setPartnerSummary(opportunity.partnerName());
        entity.setPreliminaryIncome(opportunity.preliminaryReturn());
        entity.setStatus(opportunity.status().name());
        entity.setScreeningConclusion(opportunity.reviewConclusion());
        entity.setConvertedTime(opportunity.convertedAt());
        entity.setVersion(opportunity.version());
        entity.setDeleteToken(0L);
        return entity;
    }

    private void validateConversion(InvestmentOpportunity opportunity) {
        if (opportunity.convertedInvestmentProjectId() == null) {
            return;
        }
        InvestmentProjectEntity investment = investmentProjectMapper.selectById(
                opportunity.convertedInvestmentProjectId());
        if (investment == null
                || !opportunity.convertedProjectId().equals(investment.getProjectId())) {
            throw new BusinessException("B0610", "投资机会转化项目与投资事项不一致");
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
