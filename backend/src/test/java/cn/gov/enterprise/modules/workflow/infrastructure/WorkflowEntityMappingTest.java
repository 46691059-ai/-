package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowAuditedEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskActionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTransitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeExecutionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowTaskAssignmentSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class WorkflowEntityMappingTest {
    @Test
    void entitiesShouldMapExactlyToV250Tables() throws Exception {
        assertTable(WorkflowDefinitionEntity.class, "workflow_definition");
        assertTable(WorkflowVersionEntity.class, "workflow_version");
        assertTable(WorkflowNodeEntity.class, "workflow_node");
        assertTable(WorkflowInstanceEntity.class, "workflow_instance");
        assertTable(WorkflowTaskEntity.class, "workflow_task");
        assertTable(WorkflowTaskActionEntity.class, "workflow_task_action");
        assertTable(WorkflowVersionReleaseEntity.class, "workflow_version_release");
        assertTable(WorkflowTransitionEntity.class, "workflow_transition");
        assertTable(WorkflowNodeExecutionEntity.class, "workflow_node_execution");
        assertTable(WorkflowTaskAssignmentSnapshotEntity.class, "workflow_task_assignment_snapshot");
        assertTable(WorkflowVersionNodeResolverBindingEntity.class,
                "workflow_version_node_resolver_binding");
        assertTable(WorkflowVersionResolverBindingManifestEntity.class,
                "workflow_version_resolver_binding_manifest");
        assertThat(field("deleted").getAnnotation(TableLogic.class)).isNotNull();
        assertThat(field("version").getAnnotation(Version.class)).isNotNull();
        assertThat(field("createdBy")).isNotNull();
        assertThat(field("createdTime")).isNotNull();
        assertThat(field("updatedBy")).isNotNull();
        assertThat(field("updatedTime")).isNotNull();
        assertThat(field("deleteToken")).isNotNull();
    }

    private void assertTable(Class<?> type, String table) {
        assertThat(type.getAnnotation(TableName.class)).isNotNull();
        assertThat(type.getAnnotation(TableName.class).value()).isEqualTo(table);
    }

    private Field field(String name) throws NoSuchFieldException {
        return WorkflowAuditedEntity.class.getDeclaredField(name);
    }
}
