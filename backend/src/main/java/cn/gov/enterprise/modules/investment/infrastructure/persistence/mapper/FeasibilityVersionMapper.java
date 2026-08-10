package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.FeasibilityVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeasibilityVersionMapper extends BaseMapperX<FeasibilityVersionEntity> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM investment_feasibility_version "
            + "WHERE feasibility_id = #{id} AND deleted = 0")
    int selectMaxVersionNo(@Param("id") Long feasibilityId);
}
