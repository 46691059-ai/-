package cn.gov.enterprise.modules.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectReferenceMapper {
    @Select("""
        SELECT COUNT(1)
        FROM sys_org
        WHERE id = #{orgId} AND status = 1 AND deleted = 0
        """)
    long countActiveOrg(@Param("orgId") Long orgId);

    @Select("""
        SELECT COUNT(1)
        FROM sys_user
        WHERE id = #{userId} AND org_id = #{orgId} AND status = 1 AND deleted = 0
        """)
    long countActiveUserInOrg(
            @Param("userId") Long userId,
            @Param("orgId") Long orgId);

    @Select("""
        SELECT COUNT(1)
        FROM sys_user
        WHERE id = #{userId} AND status = 1 AND deleted = 0
        """)
    long countActiveUser(@Param("userId") Long userId);
}
