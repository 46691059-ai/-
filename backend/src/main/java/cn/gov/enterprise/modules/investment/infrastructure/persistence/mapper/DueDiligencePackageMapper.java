package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligencePackageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DueDiligencePackageMapper extends BaseMapperX<DueDiligencePackageEntity> {
    @Select("SELECT COALESCE(MAX(package_version), 0) FROM investment_due_diligence_package "
            + "WHERE investment_id = #{id} AND deleted = 0")
    int selectMaxVersion(@Param("id") Long investmentId);

    @Update("UPDATE investment_due_diligence_package p "
            + "JOIN investment_due_diligence d ON d.package_id = p.id AND d.deleted = 0 "
            + "SET p.open_blocking_count = p.open_blocking_count + 1, p.version = p.version + 1 "
            + "WHERE d.id = #{reportId} AND p.deleted = 0")
    int incrementBlockingByReport(@Param("reportId") Long reportId);

    @Select("SELECT investment_id FROM investment_due_diligence_package "
            + "WHERE id = #{id} AND deleted = 0")
    Long selectInvestmentId(@Param("id") Long packageId);
}
