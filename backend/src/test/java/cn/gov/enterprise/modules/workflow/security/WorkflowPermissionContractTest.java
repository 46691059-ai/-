package cn.gov.enterprise.modules.workflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowDefinitionCommand;
import cn.gov.enterprise.modules.workflow.application.command.CreateWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.command.ReplaceWorkflowNodesCommand;
import cn.gov.enterprise.modules.workflow.application.command.PublishWorkflowVersionCommand;
import cn.gov.enterprise.modules.workflow.application.security.WorkflowPermissions;
import cn.gov.enterprise.modules.workflow.interfaces.rest.WorkflowDefinitionController;
import cn.gov.enterprise.modules.workflow.application.command.StartWorkflowCommand;
import cn.gov.enterprise.modules.workflow.interfaces.rest.WorkflowRuntimeController;
import cn.gov.enterprise.modules.workflow.application.command.ProcessWorkflowTaskCommand;
import cn.gov.enterprise.modules.workflow.interfaces.rest.WorkflowTaskController;
import cn.gov.enterprise.modules.workflow.interfaces.rest.WorkflowTransitionController;
import cn.gov.enterprise.modules.workflow.interfaces.rest.WorkflowNodeExecutionController;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class WorkflowPermissionContractTest {
    @Test
    void shouldExposeOnlyTheFrozenDefinitionPermissions() {
        assertThat(WorkflowPermissions.ALL).containsExactlyInAnyOrderElementsOf(Set.of(
                "workflow:manage",
                "workflow:definition:view", "workflow:definition:create",
                "workflow:definition:edit", "workflow:definition:publish",
                "workflow:view", "workflow:start", "workflow:approve", "workflow:withdraw"));
        assertThatThrownBy(() -> WorkflowPermissions.ALL.add("workflow:unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void taskActionEndpointsShouldDeclareFrozenAuthorities() throws Exception {
        assertPermission(WorkflowTaskController.class.getMethod(
                "approve", Long.class, ProcessWorkflowTaskCommand.class), "workflow:approve");
        assertPermission(WorkflowTaskController.class.getMethod(
                "reject", Long.class, ProcessWorkflowTaskCommand.class), "workflow:approve");
        assertPermission(WorkflowTaskController.class.getMethod(
                "withdraw", Long.class, ProcessWorkflowTaskCommand.class), "workflow:withdraw");
    }

    @Test
    void runtimeEndpointsShouldDeclareStartAndViewAuthorities() throws Exception {
        assertPermission(WorkflowRuntimeController.class.getMethod(
                "start", StartWorkflowCommand.class), "workflow:start");
        assertPermission(WorkflowRuntimeController.class.getMethod(
                "detail", Long.class), "workflow:view");
        assertPermission(WorkflowRuntimeController.class.getMethod(
                "tasks", Long.class), "workflow:view");
        assertPermission(WorkflowTransitionController.class.getMethod(
                "transitions", Long.class, Long.class), "workflow:definition:view");
        assertPermission(WorkflowNodeExecutionController.class.getMethod(
                "trajectory", Long.class), "workflow:view");
    }

    @Test
    void controllerEndpointsShouldDeclareMatchingAuthorities() throws Exception {
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "create", CreateWorkflowDefinitionCommand.class), "workflow:definition:create");
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "detail", Long.class), "workflow:definition:view");
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "versionDetail", Long.class, Long.class), "workflow:definition:view");
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "createVersion", Long.class, CreateWorkflowVersionCommand.class), "workflow:definition:edit");
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "replaceNodes", Long.class, Long.class, ReplaceWorkflowNodesCommand.class),
                "workflow:definition:edit");
        assertPermission(WorkflowDefinitionController.class.getMethod(
                "publish", Long.class, Long.class, PublishWorkflowVersionCommand.class),
                "workflow:definition:publish");
    }

    private void assertPermission(Method method, String authority) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(authority);
    }
}
