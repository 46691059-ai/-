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
        FROM hr_employee
        WHERE id = #{employeeId} AND org_id = #{orgId} AND status = 1 AND deleted = 0
        """)
    long countActiveEmployeeInOrg(
            @Param("employeeId") Long employeeId,
            @Param("orgId") Long orgId);

    @Select("""
        SELECT COUNT(1)
        FROM hr_employee
        WHERE id = #{employeeId} AND status = 1 AND deleted = 0
        """)
    long countActiveEmployee(@Param("employeeId") Long employeeId);
}
