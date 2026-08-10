package cn.gov.enterprise.modules.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

class ProjectEntityMappingTest {

    @Test
    void projectInfoFieldsMatchDatabaseColumns() {
        assertColumns(ProjectEntity.class, "project_info", Map.ofEntries(
                Map.entry("sourceType", "source_type"),
                Map.entry("customerId", "customer_id"),
                Map.entry("contractAmount", "contract_amount"),
                Map.entry("actualIncome", "actual_income"),
                Map.entry("actualProfit", "actual_profit"),
                Map.entry("description", "description"),
                Map.entry("deleteToken", "delete_token")));
    }

    @Test
    void projectStageDeleteTokenMatchesDatabaseColumn() {
        assertColumns(ProjectStageEntity.class, "project_stage", Map.of(
                "lifecycleInstanceId", "lifecycle_instance_id",
                "stageSnapshotId", "stage_snapshot_id",
                "deleteToken", "delete_token"));
    }

    @Test
    void projectTaskFieldsMatchDatabaseColumns() {
        assertColumns(ProjectTaskEntity.class, "project_task", Map.of(
                "taskContent", "task_content",
                "planStart", "plan_start",
                "planEnd", "plan_end",
                "actualStart", "actual_start",
                "actualEnd", "actual_end",
                "deleteToken", "delete_token"));
    }

    @Test
    void projectMemberDeleteTokenMatchesDatabaseColumn() {
        assertColumns(ProjectMemberEntity.class, "project_member", Map.of(
                "deleteToken", "delete_token"));
    }

    @Test
    void lifecycleInstanceFieldsMatchMigrationColumns() {
        assertColumns(ProjectLifecycleInstanceEntity.class, "project_lifecycle_instance", Map.of(
                "templateVersionId", "template_version_id",
                "templateCodeSnapshot", "template_code_snapshot",
                "progressPolicySnapshot", "progress_policy_snapshot",
                "snapshotStatus", "snapshot_status",
                "deleteToken", "delete_token"));
    }

    @Test
    void lifecycleSnapshotFieldsMatchMigrationColumns() {
        assertColumns(
                ProjectLifecycleStageSnapshotEntity.class,
                "project_lifecycle_stage_snapshot",
                Map.of(
                        "lifecycleInstanceId", "lifecycle_instance_id",
                        "sourceStageTemplateId", "source_stage_template_id",
                        "conditionSnapshotStatus", "condition_snapshot_status",
                        "definitionChecksum", "definition_checksum",
                        "deleteToken", "delete_token"));
    }

    @Test
    void lifecycleStageTemplateFieldsMatchMigrationColumns() {
        assertColumns(
                ProjectLifecycleStageTemplateEntity.class,
                "project_lifecycle_stage_template",
                Map.of(
                        "templateVersionId", "template_version_id",
                        "progressWeight", "progress_weight",
                        "approvalSceneCode", "approval_scene_code",
                        "completionMode", "completion_mode",
                        "deleteToken", "delete_token"));
    }

    private static void assertColumns(
            Class<?> entityType, String expectedTable, Map<String, String> expectedColumns) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityType.getName());
        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, entityType);
        Map<String, TableFieldInfo> fields = tableInfo.getFieldList().stream()
                .collect(Collectors.toMap(TableFieldInfo::getProperty, Function.identity()));

        assertThat(tableInfo.getTableName()).isEqualTo(expectedTable);
        assertThat(tableInfo.getKeyProperty()).isEqualTo("id");
        expectedColumns.forEach((property, column) -> {
            assertThat(fields).containsKey(property);
            assertThat(fields.get(property).getColumn()).isEqualTo(column);
        });
    }
}
