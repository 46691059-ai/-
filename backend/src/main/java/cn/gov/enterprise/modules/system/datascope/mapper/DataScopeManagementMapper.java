package cn.gov.enterprise.modules.system.datascope.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 数据权限运营配置所需的关系查询和安全逻辑删除。 */
@Mapper
public interface DataScopeManagementMapper {
    @Select("""
            SELECT ro.org_id
            FROM sys_role_org ro
            JOIN sys_org o ON o.id = ro.org_id
                          AND o.deleted = 0
                          AND o.status = 1
                          AND o.org_type <> 'PARTY_ORG'
            WHERE ro.role_id = #{roleId} AND ro.deleted = 0
            ORDER BY ro.org_id
            """)
    List<Long> selectActiveRoleOrganizationIds(@Param("roleId") Long roleId);

    @Select("""
            <script>
            SELECT id
            FROM sys_org
            WHERE deleted = 0
              AND status = 1
              AND org_type &lt;&gt; 'PARTY_ORG'
              AND id IN
              <foreach collection="orgIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY id
            </script>
            """)
    List<Long> selectValidAdministrativeOrgIds(@Param("orgIds") List<Long> orgIds);

    @Select("SELECT DISTINCT user_id FROM sys_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<Long> selectUserIdsByRole(@Param("roleId") Long roleId);

    @Update("""
            UPDATE sys_role_org
            SET deleted = 1,
                delete_token = id,
                update_time = CURRENT_TIMESTAMP(3),
                update_by = #{operator},
                version = version + 1
            WHERE role_id = #{roleId} AND deleted = 0
            """)
    int logicallyDeleteRoleOrganizations(
            @Param("roleId") Long roleId,
            @Param("operator") String operator);
}
