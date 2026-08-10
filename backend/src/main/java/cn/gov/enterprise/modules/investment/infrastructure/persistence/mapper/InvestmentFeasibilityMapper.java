package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentFeasibilityEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvestmentFeasibilityMapper extends BaseMapperX<InvestmentFeasibilityEntity> {
    @Select("SELECT * FROM investment_feasibility WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    InvestmentFeasibilityEntity selectByIdForUpdate(@Param("id") Long id);
}
