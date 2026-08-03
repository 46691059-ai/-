package cn.gov.enterprise.security;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecurityIdentityMapper {
    @Select("""
        SELECT id, username, org_id, status, token_version, locked_until
        FROM sys_user
        WHERE id = #{userId} AND deleted = 0
        """)
    SecurityUserRow selectUser(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
        JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        """)
    List<String> selectPermissions(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT r.role_code
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        ORDER BY r.role_code
        """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.menu_type, m.path,
               m.component, m.permission, m.icon, m.sort_no, m.visible
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
        JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        ORDER BY m.sort_no, m.id
        """)
    List<SecurityMenuRow> selectMenus(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT r.id AS roleId, r.data_scope_type AS dataScopeType
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        ORDER BY r.id
        """)
    List<RoleScopeRow> selectRoleScopes(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT r.data_scope_type
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        """)
    List<String> selectDataScopeTypes(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT o.id
        FROM sys_user_role ur
        JOIN sys_role_org ro ON ro.role_id = ur.role_id AND ro.deleted = 0
        JOIN sys_org o ON o.id = ro.org_id
                      AND o.deleted = 0
                      AND o.status = 1
                      AND o.org_type <> 'PARTY_ORG'
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        """)
    List<Long> selectCustomOrgIds(@Param("userId") Long userId);

    @Select("""
        SELECT id
        FROM sys_org
        WHERE deleted = 0 AND status = 1
          AND (id = #{orgId} OR tree_path LIKE CONCAT('%/', #{orgId}, '/%'))
        """)
    List<Long> selectOrgAndChildren(@Param("orgId") Long orgId);

    record RoleScopeRow(Long roleId, String dataScopeType) {
    }

    record SecurityUserRow(
            Long id,
            String username,
            Long orgId,
            Integer status,
            Integer tokenVersion,
            LocalDateTime lockedUntil) {
    }

    record SecurityMenuRow(
            Long id, Long parentId, String menuName, String menuType, String path,
            String component, String permission, String icon, Integer sortNo, Integer visible) {
    }
}
