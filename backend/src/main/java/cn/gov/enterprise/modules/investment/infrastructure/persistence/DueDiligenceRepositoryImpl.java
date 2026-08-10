package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligencePackage;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceReport;
import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceType;
import cn.gov.enterprise.modules.investment.domain.repository.DueDiligenceRepository;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceItemEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligencePackageEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceReportEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligenceItemMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligencePackageMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligenceReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class DueDiligenceRepositoryImpl implements DueDiligenceRepository {
    private final DueDiligencePackageMapper packageMapper;
    private final DueDiligenceReportMapper reportMapper;
    private final DueDiligenceItemMapper itemMapper;

    public DueDiligenceRepositoryImpl(
            DueDiligencePackageMapper packageMapper,
            DueDiligenceReportMapper reportMapper,
            DueDiligenceItemMapper itemMapper) {
        this.packageMapper = packageMapper;
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
    }

    @Override public int nextPackageVersion(Long investmentId) {
        return packageMapper.selectMaxVersion(investmentId) + 1;
    }
    @Override public void savePackage(DueDiligencePackage value) {
        insert(() -> packageMapper.insert(toEntity(value)), "尽调包版本冲突");
    }
    @Override public Optional<DueDiligencePackage> findPackage(Long id) {
        return Optional.ofNullable(packageMapper.selectById(id)).map(this::toDomain);
    }
    @Override public Optional<Long> findInvestmentIdByPackageId(Long id) {
        return Optional.ofNullable(packageMapper.selectInvestmentId(id));
    }
    @Override public List<DueDiligencePackage> findPackages(Long investmentId) {
        return packageMapper.selectList(new LambdaQueryWrapper<DueDiligencePackageEntity>()
                        .eq(DueDiligencePackageEntity::getInvestmentId, investmentId)
                        .orderByDesc(DueDiligencePackageEntity::getPackageVersion))
                .stream().map(this::toDomain).toList();
    }
    @Override public int nextReportVersion(Long packageId, DueDiligenceType type) {
        return reportMapper.selectMaxVersion(packageId, type.name()) + 1;
    }
    @Override public void appendReport(DueDiligenceReport value) {
        insert(() -> reportMapper.insert(toEntity(value)), "尽调报告版本冲突");
    }
    @Override public Optional<DueDiligenceReport> findReport(Long id) {
        return Optional.ofNullable(reportMapper.selectById(id)).map(this::toDomain);
    }
    @Override public Optional<Long> findInvestmentIdByReportId(Long id) {
        return Optional.ofNullable(reportMapper.selectInvestmentId(id));
    }
    @Override public List<DueDiligenceReport> findReports(Long packageId) {
        return reportMapper.selectList(new LambdaQueryWrapper<DueDiligenceReportEntity>()
                        .eq(DueDiligenceReportEntity::getPackageId, packageId)
                        .orderByAsc(DueDiligenceReportEntity::getDueDiligenceType)
                        .orderByDesc(DueDiligenceReportEntity::getReportVersion))
                .stream().map(this::toDomain).toList();
    }
    @Override public void saveItem(DueDiligenceItem value) {
        insert(() -> itemMapper.insert(toEntity(value)), "尽调问题编号冲突");
        int material = value.severity() == DueDiligenceItem.Severity.HIGH
                || value.severity() == DueDiligenceItem.Severity.CRITICAL ? 1 : 0;
        if (reportMapper.incrementRiskCounts(value.reportId(), material) != 1) {
            throw new BusinessException("B0650", "尽调报告风险统计更新失败");
        }
        if (value.blocking() && packageMapper.incrementBlockingByReport(value.reportId()) != 1) {
            throw new BusinessException("B0650", "尽调包阻断问题统计更新失败");
        }
    }
    @Override public List<DueDiligenceItem> findItems(Long reportId) {
        return itemMapper.selectList(new LambdaQueryWrapper<DueDiligenceItemEntity>()
                        .eq(DueDiligenceItemEntity::getDueDiligenceId, reportId)
                        .orderByDesc(DueDiligenceItemEntity::getSeverity)
                        .orderByAsc(DueDiligenceItemEntity::getItemNo))
                .stream().map(this::toDomain).toList();
    }
    @Override public boolean existsItemNo(Long reportId, String itemNo) {
        return itemMapper.selectCount(new LambdaQueryWrapper<DueDiligenceItemEntity>()
                .eq(DueDiligenceItemEntity::getDueDiligenceId, reportId)
                .eq(DueDiligenceItemEntity::getItemNo, itemNo)) > 0;
    }

    private DueDiligencePackage toDomain(DueDiligencePackageEntity e) {
        Set<DueDiligenceType> types = Arrays.stream(e.getRequiredTypes().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(DueDiligenceType::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new DueDiligencePackage(e.getId(), e.getInvestmentId(), e.getPackageVersion(),
                e.getRuleVersion(), types, DueDiligencePackage.Conclusion.valueOf(e.getOverallConclusion()),
                e.getOpenBlockingCount(), DueDiligencePackage.Status.valueOf(e.getStatus()), e.getVersion());
    }
    private DueDiligencePackageEntity toEntity(DueDiligencePackage v) {
        DueDiligencePackageEntity e = new DueDiligencePackageEntity();
        e.setId(v.id()); e.setInvestmentId(v.investmentProjectId());
        e.setPackageVersion(v.packageVersion()); e.setRuleVersion(v.ruleVersion());
        e.setRequiredTypes(v.requiredTypesSnapshot()); e.setOverallConclusion(v.overallConclusion().name());
        e.setOpenBlockingCount(v.openBlockingCount()); e.setStatus(v.status().name());
        e.setDeleteToken(0L); e.setVersion(v.version()); return e;
    }
    private DueDiligenceReport toDomain(DueDiligenceReportEntity e) {
        return new DueDiligenceReport(e.getId(), e.getPackageId(), e.getInvestmentId(),
                DueDiligenceType.valueOf(e.getDueDiligenceType()), e.getReportVersion(),
                e.getReportNo(), e.getReportName(), e.getEntrustedOrg(), e.getLeadPersonId(),
                e.getStartDate(), e.getEndDate(), e.getBaseDate(),
                DueDiligenceReport.Conclusion.valueOf(e.getConclusion()), e.getConclusionSummary(),
                e.getMaterialRiskCount(), e.getUnresolvedRiskCount(), e.getPrimaryFileId(),
                DueDiligenceReport.Status.valueOf(e.getStatus()), e.getVersion());
    }
    private DueDiligenceReportEntity toEntity(DueDiligenceReport v) {
        DueDiligenceReportEntity e = new DueDiligenceReportEntity();
        e.setId(v.id()); e.setPackageId(v.packageId()); e.setInvestmentId(v.investmentProjectId());
        e.setDueDiligenceType(v.type().name()); e.setReportVersion(v.reportVersion());
        e.setReportNo(v.reportNo()); e.setReportName(v.reportName()); e.setEntrustedOrg(v.entrustedOrg());
        e.setLeadPersonId(v.leadPersonId()); e.setStartDate(v.startDate()); e.setEndDate(v.endDate());
        e.setBaseDate(v.baseDate()); e.setConclusion(v.conclusion().name());
        e.setConclusionSummary(v.conclusionSummary()); e.setMaterialRiskCount(v.materialRiskCount());
        e.setUnresolvedRiskCount(v.unresolvedRiskCount()); e.setPrimaryFileId(v.primaryFileId());
        e.setStatus(v.status().name()); e.setDeleteToken(0L); e.setVersion(v.version()); return e;
    }
    private DueDiligenceItem toDomain(DueDiligenceItemEntity e) {
        return new DueDiligenceItem(e.getId(), e.getDueDiligenceId(), e.getInvestmentId(),
                e.getItemNo(), e.getCategory(), DueDiligenceItem.Severity.valueOf(e.getSeverity()),
                Integer.valueOf(1).equals(e.getBlockingFlag()), e.getProblemDescription(),
                e.getImpactDescription(), e.getRectificationMeasure(), e.getResponsibleOrgId(),
                e.getResponsiblePersonId(), e.getDeadline(), DueDiligenceItem.Status.valueOf(e.getStatus()),
                e.getVersion());
    }
    private DueDiligenceItemEntity toEntity(DueDiligenceItem v) {
        DueDiligenceItemEntity e = new DueDiligenceItemEntity();
        e.setId(v.id()); e.setDueDiligenceId(v.reportId()); e.setInvestmentId(v.investmentProjectId());
        e.setItemNo(v.itemNo()); e.setCategory(v.category()); e.setSeverity(v.severity().name());
        e.setBlockingFlag(v.blocking() ? 1 : 0); e.setProblemDescription(v.problemDescription());
        e.setImpactDescription(v.impactDescription()); e.setRectificationMeasure(v.rectificationMeasure());
        e.setResponsibleOrgId(v.responsibleOrgId()); e.setResponsiblePersonId(v.responsiblePersonId());
        e.setDeadline(v.deadline()); e.setStatus(v.status().name()); e.setDeleteToken(0L);
        e.setVersion(v.version()); return e;
    }
    private void insert(IntInsert action, String conflictMessage) {
        try {
            if (action.run() != 1) throw new BusinessException("B0650", "尽调数据保存失败");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("B0659", conflictMessage);
        }
    }
    @FunctionalInterface private interface IntInsert { int run(); }
}
