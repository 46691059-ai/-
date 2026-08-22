package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowMultiResolverRuntimeContractTest {
    @Test
    void startMustPrepareAndPersistBindingsInsideTransactionalBoundary() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/workflow/"
                + "application/service/WorkflowRuntimeApplicationService.java"));
        assertThat(source).contains("@Transactional", "bindingService.prepare(",
                "instanceRepository.save(instance)", "bindingService.save(frozenBindings)",
                "frozenBindings.requireNode(entry.id())");
        assertThat(source.indexOf("instanceRepository.save(instance)"))
                .isLessThan(source.lastIndexOf("bindingService.save(frozenBindings)"));
    }

    @Test
    void nextTaskMustPreferFrozenNodeBindingAndKeepExplicitLegacyPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/workflow/"
                + "application/service/WorkflowLinearExecutionApplicationService.java"));
        assertThat(source).contains("bindingService.findNode(instance.id(), nextNode.id())",
                "nodeBinding.requireExplicitUserId()", "bindingService.runtimeResolver(nodeBinding)",
                "resolverService.selectForExistingInstance(instance.requireResolverVersionBinding())",
                "multi-resolver instance has a missing node binding");
        assertThat(source).doesNotContain("resolverService.select(instance.requireResolverVersionBinding())");
    }

    @Test
    void bindingApiMustRemainReadOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/gov/enterprise/modules/workflow/"
                + "interfaces/rest/WorkflowRuntimeController.java"));
        assertThat(source).contains("/{instanceId}/resolver-bindings",
                "/{instanceId}/nodes/{nodeId}/resolver-binding", "workflow:view");
        assertThat(source).doesNotContain("@PutMapping", "@DeleteMapping");
    }
}
