package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentOpportunityEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.row.InvestmentOpportunityRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InvestmentOpportunityMapper extends BaseMapperX<InvestmentOpportunityEntity> {
    @Select("""
        SELECT io.*, ip.project_id AS converted_project_id
        FROM investment_opportunity io
        LEFT JOIN investment_project ip
          ON ip.id = io.investment_id AND ip.deleted = 0
        WHERE io.id = #{id} AND io.deleted = 0
        """)
    InvestmentOpportunityRow selectDomainRowById(@Param("id") Long id);

    @Select("""
        SELECT io.*, ip.project_id AS converted_project_id
        FROM investment_opportunity io
        LEFT JOIN investment_project ip
          ON ip.id = io.investment_id AND ip.deleted = 0
        WHERE io.id = #{id} AND io.deleted = 0
        FOR UPDATE
        """)
    InvestmentOpportunityRow selectDomainRowByIdForUpdate(@Param("id") Long id);

    @Update("""
        UPDATE investment_opportunity
        SET status = #{status},
            screening_conclusion = #{reviewConclusion},
            investment_id = #{investmentId},
            converted_time = #{convertedTime},
            update_time = CURRENT_TIMESTAMP(3),
            update_by = #{operator},
            version = version + 1
        WHERE id = #{id}
          AND status = #{expectedStatus}
          AND version = #{expectedVersion}
          AND deleted = 0
        """)
    int updateState(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("reviewConclusion") String reviewConclusion,
            @Param("investmentId") Long investmentId,
            @Param("convertedTime") java.time.LocalDateTime convertedTime,
            @Param("operator") String operator,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") int expectedVersion);
}
