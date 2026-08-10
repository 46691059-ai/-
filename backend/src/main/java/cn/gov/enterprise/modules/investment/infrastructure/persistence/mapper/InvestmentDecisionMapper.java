package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentDecisionEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.DecisionMaterialsRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvestmentDecisionMapper extends BaseMapperX<InvestmentDecisionEntity> {
    @Select("SELECT * FROM investment_decision WHERE id=#{id} AND deleted=0 FOR UPDATE")
    InvestmentDecisionEntity selectForUpdate(@Param("id") Long id);

    @Select("""
        SELECT sv.id scheme_version_id, sv.status scheme_status, sv.content_hash scheme_hash,
               fv.id feasibility_version_id, fv.status feasibility_status,
               fv.conclusion feasibility_conclusion, fv.content_hash feasibility_hash,
               dd.id due_diligence_package_id, dd.status due_diligence_status,
               dd.overall_conclusion due_diligence_conclusion,
               dd.open_blocking_count, dd.content_hash due_diligence_hash,
               sv.investment_subject_org_id enterprise_id
        FROM investment_scheme s
        JOIN investment_scheme_version sv ON sv.id=s.current_frozen_version_id AND sv.deleted=0
        JOIN investment_feasibility_version fv ON fv.id=sv.feasibility_version_id AND fv.deleted=0
        JOIN investment_due_diligence_package dd ON dd.id=sv.due_diligence_package_id AND dd.deleted=0
        WHERE s.investment_id=#{investmentId} AND s.deleted=0
        """)
    DecisionMaterialsRow selectFrozenMaterials(@Param("investmentId") Long investmentId);
}
