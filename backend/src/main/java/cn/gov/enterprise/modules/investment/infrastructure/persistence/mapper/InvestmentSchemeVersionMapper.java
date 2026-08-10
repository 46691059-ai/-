package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.InvestmentSchemeVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InvestmentSchemeVersionMapper extends BaseMapperX<InvestmentSchemeVersionEntity> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM investment_scheme_version "
            + "WHERE scheme_id = #{id} AND deleted = 0")
    int selectMaxVersionNo(@Param("id") Long schemeId);
}
