package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.DueDiligenceReferenceRow;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.FeasibilityReferenceRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvestmentSchemeMapper extends BaseMapperX<InvestmentSchemeEntity> {
    @Select("SELECT * FROM investment_scheme WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    InvestmentSchemeEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        SELECT f.investment_id, v.status, v.conclusion
        FROM investment_feasibility_version v
        JOIN investment_feasibility f ON f.id = v.feasibility_id AND f.deleted = 0
        WHERE v.id = #{id} AND v.deleted = 0
        """)
    FeasibilityReferenceRow selectFeasibilityReference(@Param("id") Long versionId);

    @Select("""
        SELECT investment_id, status, overall_conclusion, open_blocking_count
        FROM investment_due_diligence_package
        WHERE id = #{id} AND deleted = 0
        """)
    DueDiligenceReferenceRow selectDueDiligenceReference(@Param("id") Long packageId);
}
