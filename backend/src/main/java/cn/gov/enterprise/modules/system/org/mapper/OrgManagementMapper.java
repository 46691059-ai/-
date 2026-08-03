package cn.gov.enterprise.modules.system.org.mapper;

import cn.gov.enterprise.modules.system.org.vo.LeaderOptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrgManagementMapper {
    @Select("SELECT COUNT(1) FROM sys_user WHERE org_id = #{orgId} AND deleted = 0")
    long countUsersByOrg(@Param("orgId") Long orgId);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user u
            JOIN hr_employee e ON e.id = u.employee_id AND e.deleted = 0 AND e.status = 'ACTIVE'
            WHERE u.employee_id = #{employeeId} AND u.deleted = 0 AND u.status = 1
            """)
    long countActiveLeader(@Param("employeeId") Long employeeId);

    @Select("""
            SELECT u.employee_id AS employeeId, u.id AS userId, u.username, u.real_name AS realName
            FROM sys_user u
            JOIN hr_employee e ON e.id = u.employee_id AND e.deleted = 0 AND e.status = 'ACTIVE'
            WHERE u.deleted = 0 AND u.status = 1 AND u.employee_id IS NOT NULL
            ORDER BY u.real_name, u.username
            """)
    List<LeaderOptionVO> selectLeaderOptions();

    @Select("""
            <script>
            SELECT u.employee_id AS employeeId, u.id AS userId, u.username, u.real_name AS realName
            FROM sys_user u
            WHERE u.deleted = 0 AND u.employee_id IN
            <foreach collection="employeeIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<LeaderOptionVO> selectLeaders(@Param("employeeIds") List<Long> employeeIds);
}
