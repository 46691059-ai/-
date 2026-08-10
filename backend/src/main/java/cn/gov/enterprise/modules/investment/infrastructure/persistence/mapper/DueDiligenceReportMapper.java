package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceReportEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DueDiligenceReportMapper extends BaseMapperX<DueDiligenceReportEntity> {
    @Select("SELECT COALESCE(MAX(report_version), 0) FROM investment_due_diligence "
            + "WHERE package_id = #{packageId} AND due_diligence_type = #{type} AND deleted = 0")
    int selectMaxVersion(@Param("packageId") Long packageId, @Param("type") String type);

    @Update("UPDATE investment_due_diligence SET material_risk_count = material_risk_count + #{material}, "
            + "unresolved_risk_count = unresolved_risk_count + 1, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0")
    int incrementRiskCounts(@Param("id") Long reportId, @Param("material") int material);

    @Select("SELECT investment_id FROM investment_due_diligence "
            + "WHERE id = #{id} AND deleted = 0")
    Long selectInvestmentId(@Param("id") Long reportId);
}
