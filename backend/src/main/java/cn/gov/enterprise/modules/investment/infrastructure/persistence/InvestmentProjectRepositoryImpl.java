package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.InvestmentProject;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentProjectEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.InvestmentProjectMapper;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis Plus persistence adapter for InvestmentProject. */
@Repository
public class InvestmentProjectRepositoryImpl implements InvestmentProjectRepository {
    private final InvestmentProjectMapper investmentProjectMapper;
    private final ProjectMapper projectMapper;

    public InvestmentProjectRepositoryImpl(
            InvestmentProjectMapper investmentProjectMapper,
            ProjectMapper projectMapper) {
        this.investmentProjectMapper = investmentProjectMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public Optional<InvestmentProject> findById(Long investmentProjectId) {
        InvestmentProjectEntity entity = investmentProjectMapper.selectById(investmentProjectId);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public Optional<InvestmentProject> findByProjectId(Long projectId) {
        InvestmentProjectEntity entity = investmentProjectMapper.selectOne(
                new LambdaQueryWrapper<InvestmentProjectEntity>()
                        .eq(InvestmentProjectEntity::getProjectId, projectId));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public Optional<Long> findProjectIdById(Long investmentProjectId) {
        return Optional.ofNullable(investmentProjectMapper.selectProjectIdById(investmentProjectId));
    }

    @Override
    public boolean existsByInvestmentNo(String investmentNo) {
        return investmentProjectMapper.selectCount(
                new LambdaQueryWrapper<InvestmentProjectEntity>()
                        .eq(InvestmentProjectEntity::getInvestmentNo, investmentNo)) > 0;
    }

    @Override
    public void save(InvestmentProject investmentProject) {
        ProjectEntity project = requireProject(investmentProject.projectId());
        if (!project.getDepartmentId().equals(investmentProject.responsibleOrgId())
                || !project.getLeaderId().equals(investmentProject.ownerId())) {
            throw new BusinessException("B0600", "投资事项归属必须与关联项目一致");
        }
        try {
            if (investmentProjectMapper.insert(toEntity(investmentProject)) != 1) {
                throw new BusinessException("B0600", "投资事项保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0609", "投资编号或关联项目已存在");
        }
    }

    private InvestmentProject toDomain(InvestmentProjectEntity entity) {
        ProjectEntity project = requireProject(entity.getProjectId());
        try {
            return new InvestmentProject(
                    entity.getId(), entity.getProjectId(), entity.getInvestmentNo(),
                    entity.getInvestmentName(),
                    InvestmentProject.InvestmentType.valueOf(entity.getInvestmentType()),
                    InvestmentProject.InvestmentMethod.valueOf(entity.getInvestmentMethod()),
                    valueOrZero(entity.getTotalAmount()), entity.getInvestmentRatio(),
                    project.getDepartmentId(), project.getLeaderId(),
                    InvestmentProject.Status.valueOf(entity.getStatus()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("B0600", "投资事项数据不符合当前领域模型");
        }
    }

    private InvestmentProjectEntity toEntity(InvestmentProject project) {
        InvestmentProjectEntity entity = new InvestmentProjectEntity();
        entity.setId(project.id());
        entity.setProjectId(project.projectId());
        entity.setInvestmentNo(project.investmentNo());
        entity.setInvestmentName(project.investmentName());
        entity.setInvestmentType(project.investmentType().name());
        entity.setInvestmentMethod(project.investmentMethod().name());
        entity.setTotalAmount(project.investmentAmount());
        entity.setOwnCapital(BigDecimal.ZERO);
        entity.setFinancingAmount(BigDecimal.ZERO);
        entity.setInvestmentRatio(project.investmentRatio());
        entity.setExpectedIncome(BigDecimal.ZERO);
        entity.setRiskLevel("LOW");
        entity.setApprovalStatus("NOT_SUBMITTED");
        entity.setStatus(project.status().name());
        entity.setDeleteToken(0L);
        return entity;
    }

    private ProjectEntity requireProject(Long projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("B0404", "关联项目不存在");
        }
        if (project.getDepartmentId() == null || project.getLeaderId() == null) {
            throw new BusinessException("B0600", "关联项目缺少责任组织或负责人");
        }
        return project;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
