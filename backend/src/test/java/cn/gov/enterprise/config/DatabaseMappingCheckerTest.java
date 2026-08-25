package cn.gov.enterprise.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.system.entity.SysLogEntity;
import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysOrgEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysUserEntity;
import cn.gov.enterprise.modules.system.entity.SysUserRoleEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatabaseMappingCheckerTest {
    private static final Set<String> COMMON_COLUMNS = Set.of(
            "id", "create_time", "create_by", "update_time", "update_by",
            "deleted", "delete_token", "remark", "version");
    private static final Set<String> WORKFLOW_AUDIT_COLUMNS = Set.of(
            "id", "created_time", "created_by", "updated_time", "updated_by",
            "deleted", "delete_token", "remark", "version");

    @Test
    void systemEntitiesStrictlyMatchSystemTableColumns() {
        assertColumns(SysUserEntity.class,
                "username", "password", "real_name", "phone", "email", "org_id", "employee_id",
                "status", "token_version", "login_fail_count", "locked_until", "last_login_time", "last_login_ip");
        assertColumns(SysRoleEntity.class,
                "role_name", "role_code", "description", "data_scope_type", "status");
        assertColumns(SysMenuEntity.class,
                "menu_code", "menu_name", "parent_id", "menu_type", "path", "component", "permission", "icon",
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

    @Test
    void v2621WorkflowEntitiesMustExposeEveryOrdinarySchemaColumn() {
        assertWorkflowColumns(WorkflowVersionEntity.class,
                "definition_id", "version_no", "status", "schema_version", "engine_mode",
                "content_hash_algorithm", "resolver_binding_model", "resolver_binding_manifest_hash",
                "resolver_binding_count", "resolver_binding_canonical_version", "content_hash",
                "change_note", "effective_from", "effective_to", "published_by", "published_time",
                "source_version_id");
        assertWorkflowColumns(WorkflowVersionReleaseEntity.class,
                "definition_id", "previous_version_id", "published_version_id", "published_version_no",
                "content_hash", "engine_mode", "content_hash_algorithm", "resolver_binding_model",
                "resolver_binding_manifest_hash", "resolver_binding_count",
                "resolver_binding_canonical_version", "operator_user_id", "operator_org_id",
                "published_time", "trace_id", "validation_summary");
        assertWorkflowColumns(WorkflowVersionNodeResolverBindingEntity.class,
                "definition_id", "definition_version_id", "node_id", "binding_order",
                "resolver_code", "resolver_version", "resolver_contract_hash", "strategy_type",
                "resolver_mode", "target_type", "role_code", "organization_scope_type",
                "organization_id", "effective_time_policy", "binding_schema_version", "binding_hash");
        assertWorkflowColumns(WorkflowVersionResolverBindingManifestEntity.class,
                "definition_id", "definition_version_id", "canonical_version", "binding_count",
                "manifest_hash", "released_by", "released_time");
    }

    @Test
    void v2622NodeResolverSnapshotMustExposeEveryForwardFreezeColumn() {
        assertWorkflowColumns(WorkflowNodeResolverBindingEntity.class,
                "binding_set_id", "resolver_binding_id", "instance_id",
                "definition_version_id", "node_id", "node_code_snapshot",
                "version_binding_id", "version_binding_order", "version_binding_slot",
                "version_binding_hash", "resolver_contract_hash_snapshot", "role_code",
                "organization_scope_type", "resolved_organization_id",
                "effective_time_policy", "binding_schema_version", "strategy_type",
                "resolver_mode", "target_type", "target_value_snapshot", "rule_version",
                "rule_snapshot", "rule_hash", "node_binding_hash", "binding_status",
                "frozen_time", "audit_info");
    }

    @Test
    void onlyDatabaseGeneratedColumnsAreAllowedAsDatabaseSuperset() {
        Set<String> databaseOnly = Set.of("decision_terminal_token", "ordinary_unmapped_column");
        Map<String, DatabaseMappingChecker.DatabaseColumn> metadata = Map.of(
                "decision_terminal_token",
                new DatabaseMappingChecker.DatabaseColumn("decision_terminal_token", true),
                "ordinary_unmapped_column",
                new DatabaseMappingChecker.DatabaseColumn("ordinary_unmapped_column", false));

        assertThat(DatabaseMappingChecker.generatedColumns(databaseOnly, metadata))
                .containsExactly("decision_terminal_token");
    }

    private void assertColumns(Class<?> entityType, String... businessColumns) {
        Set<String> expected = new java.util.HashSet<>(COMMON_COLUMNS);
        expected.addAll(Set.of(businessColumns));
        assertThat(DatabaseMappingChecker.resolveEntityColumns(entityType)).isEqualTo(expected);
    }

    private void assertWorkflowColumns(Class<?> entityType, String... businessColumns) {
        Set<String> expected = new java.util.HashSet<>(WORKFLOW_AUDIT_COLUMNS);
        expected.addAll(Set.of(businessColumns));
        assertThat(DatabaseMappingChecker.resolveEntityColumns(entityType)).isEqualTo(expected);
    }
}
