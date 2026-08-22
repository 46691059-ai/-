package cn.gov.enterprise.modules.workflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.application.security.WorkflowPermissions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WorkflowRbacMigrationContractTest {
    private static final Path MIGRATION = Path.of("..", "database", "migration", "mysql",
            "V2.5.3__initialize_workflow_rbac.sql");
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("'(workflow:[a-z_:]+)'");

    @Test
    void migrationPermissionSeedMustExactlyMatchCodeConstantsAndRemainUnique() throws IOException {
        String sql = sql();
        String seedBlock = between(sql, "INSERT INTO sys_permission", "-- M/C menu stable key");
        List<String> codes = matches(seedBlock, PERMISSION_CODE);

        assertThat(codes).hasSize(9).doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<>(codes)).containsExactlyInAnyOrderElementsOf(WorkflowPermissions.ALL);
        assertThat(WorkflowPermissions.ALL).hasSize(9);
    }

    @Test
    void menuStableKeysMustBeUniqueAndFollowExistingSysMenuContract() throws IOException {
        String sql = sql();
        Set<String> routeKeys = Set.of(
                "/workflow", "/workflow/definitions", "/workflow/instances", "/workflow/tasks");
        Set<String> buttonKeys = Set.of(
                "/workflow/definitions|workflow:definition:create",
                "/workflow/definitions|workflow:definition:edit",
                "/workflow/definitions|workflow:definition:publish",
                "/workflow/instances|workflow:start",
                "/workflow/tasks|workflow:approve",
                "/workflow/tasks|workflow:withdraw");

        assertThat(routeKeys).hasSize(4);
        assertThat(buttonKeys).hasSize(6);
        routeKeys.forEach(key -> assertThat(sql).contains("'" + key + "'"));
        buttonKeys.forEach(key -> {
            String[] parts = key.split("\\|", 2);
            assertThat(sql).contains("'" + parts[0] + "'").contains("'" + parts[1] + "'");
        });
        assertThat(sql).contains("WHERE m.path = s.path AND m.deleted = 0")
                .contains("m.parent_id = parent.id AND m.menu_type = 'B'")
                .contains("m.permission = s.permission AND m.deleted = 0");
    }

    @Test
    void onlySuperAdminMustReceiveCompletePermissionAndMenuGrants() throws IOException {
        String sql = sql();
        String grants = sql.substring(sql.indexOf("-- Only SUPER_ADMIN"));
        List<String> grantedCodes = matches(
                between(grants, "p.permission_code IN (", ") AND p.deleted = 0"),
                PERMISSION_CODE);

        assertThat(grantedCodes).hasSize(9).doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<>(grantedCodes))
                .containsExactlyInAnyOrderElementsOf(WorkflowPermissions.ALL);
        assertThat(grants).contains("r.role_code = 'SUPER_ADMIN'")
                .doesNotContain("EMPLOYEE", "PROJECT_MANAGER", "DEPARTMENT_MANAGER")
                .contains("root.path = '/workflow'")
                .contains("m.id = root.id")
                .contains("m.parent_id = root.id");
    }

    private String sql() throws IOException {
        return Files.readString(MIGRATION, StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to);
    }

    private List<String> matches(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }
}
