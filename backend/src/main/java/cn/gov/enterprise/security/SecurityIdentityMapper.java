package cn.gov.enterprise.security;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecurityIdentityMapper {
    @Select("""
        SELECT id, username, org_id, status, token_version
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
        SELECT DISTINCT r.data_scope_type
        FROM sys_user_role ur
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
        WHERE ur.user_id = #{userId} AND ur.deleted = 0
        """)
    List<String> selectDataScopeTypes(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT ro.org_id
        FROM sys_user_role ur
        JOIN sys_role_org ro ON ro.role_id = ur.role_id AND ro.deleted = 0
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

    record SecurityUserRow(Long id, String username, Long orgId, Integer status, Integer tokenVersion) {
    }
}
