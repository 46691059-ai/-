package cn.gov.enterprise.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.system.entity.SysLogEntity;
import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.entity.SysUserRoleEntity;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatabaseMappingCheckerTest {
    private static final Set<String> COMMON_COLUMNS = Set.of(
            "id", "create_time", "create_by", "update_time", "update_by",
            "deleted", "delete_token", "remark", "version");

    @Test
    void systemEntitiesStrictlyMatchSystemTableColumns() {
        assertColumns(SysUserEntity.class,
                "username", "password", "real_name", "phone", "email", "org_id", "employee_id",
                "status", "token_version", "login_fail_count", "locked_until", "last_login_time", "last_login_ip");
        assertColumns(SysRoleEntity.class,
                "role_name", "role_code", "description", "data_scope_type", "status");
        assertColumns(SysMenuEntity.class,
                "menu_name", "parent_id", "menu_type", "path", "component", "permission", "icon",
                "sort_no", "visible", "status");
        assertColumns(SysOrgEntity.class,
                "org_code", "org_name", "org_type", "parent_id", "leader_id", "tree_path", "tree_level",
                "sort_no", "status");
        assertColumns(SysUserRoleEntity.class, "user_id", "role_id");
        assertColumns(SysRoleMenuEntity.class, "role_id", "menu_id");
        assertColumns(SysLogEntity.class,
                "user_id", "operation", "request_url", "request_method", "ip", "result", "error_message",
                "duration_ms", "trace_id");
    }

    private void assertColumns(Class<?> entityType, String... businessColumns) {
        Set<String> expected = new java.util.HashSet<>(COMMON_COLUMNS);
        expected.addAll(Set.of(businessColumns));
        assertThat(DatabaseMappingChecker.resolveEntityColumns(entityType)).isEqualTo(expected);
    }
}
