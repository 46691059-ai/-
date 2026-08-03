package cn.gov.enterprise.modules.system.role.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoleManagementMapper {
    @Select("""
            <script>
            SELECT role_id AS roleId, COUNT(DISTINCT user_id) AS userCount
            FROM sys_user_role
            WHERE deleted = 0 AND role_id IN
            <foreach collection="roleIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY role_id
            </script>
            """)
    List<RoleUserCountRow> countUsersByRoles(@Param("roleIds") List<Long> roleIds);

    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId} AND deleted = 0")
    long countUsersByRole(@Param("roleId") Long roleId);

    @Update("""
            UPDATE sys_role_menu SET deleted = 1, delete_token = id,
                update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}, version = version + 1
            WHERE role_id = #{roleId} AND deleted = 0
            """)
    int logicallyDeleteRoleMenus(@Param("roleId") Long roleId, @Param("operator") String operator);

    @Update("""
            UPDATE sys_role_permission SET deleted = 1, delete_token = id,
                update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}, version = version + 1
            WHERE role_id = #{roleId} AND deleted = 0
            """)
    int logicallyDeleteRolePermissions(@Param("roleId") Long roleId, @Param("operator") String operator);

    @Update("""
            UPDATE sys_user_role SET deleted = 1, delete_token = id,
                update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}, version = version + 1
            WHERE role_id = #{roleId} AND deleted = 0
            """)
    int logicallyDeleteUserRoles(@Param("roleId") Long roleId, @Param("operator") String operator);

    @Select("SELECT DISTINCT user_id FROM sys_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<Long> selectUserIdsByRole(@Param("roleId") Long roleId);

    record RoleUserCountRow(Long roleId, Long userCount) {
    }
}
