package cn.gov.enterprise.modules.system.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserManagementMapper {

    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.id AS roleId, r.role_name AS roleName, r.role_code AS roleCode
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
            WHERE ur.deleted = 0 AND ur.user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<UserRoleRow> selectUserRoles(@Param("userIds") List<Long> userIds);

    @Select("""
            SELECT COUNT(1) FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.deleted = 0 AND ur.user_id = #{userId} AND r.role_code = 'SUPER_ADMIN'
            """)
    long countSuperAdminRole(@Param("userId") Long userId);

    @Update("""
            UPDATE sys_user_role
            SET deleted = 1, delete_token = id, update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}, version = version + 1
            WHERE user_id = #{userId} AND deleted = 0
            """)
    int logicallyDeleteUserRoles(@Param("userId") Long userId, @Param("operator") String operator);

    record UserRoleRow(Long userId, Long roleId, String roleName, String roleCode) {
    }
}
