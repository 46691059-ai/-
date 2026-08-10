package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentProjectEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvestmentProjectMapper extends BaseMapperX<InvestmentProjectEntity> {
    @Select("""
        SELECT project_id
        FROM investment_project
        WHERE id = #{id} AND deleted = 0
        """)
    Long selectProjectIdById(@Param("id") Long id);
}
