package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.WorkflowAssignmentResolverApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowAssignmentResolverApplicationServiceTest {
    private final ExplicitUserResolver resolver = new ExplicitUserResolver();
    private final WorkflowAssignmentResolverApplicationService service =
            new WorkflowAssignmentResolverApplicationService(
                    new ResolverRegistry(List.of(resolver)));

    @Test
    void listMustExposeOnlyFrozenUserDirectResolver() {
        var details = service.listAvailable();

        assertThat(details).hasSize(1);
        assertThat(details.getFirst().resolverCode()).isEqualTo("EXPLICIT_USER");
        assertThat(details.getFirst().resolverVersion()).isEqualTo("EXPLICIT_USER_V1");
        assertThat(details.getFirst().strategyType()).isEqualTo("USER");
        assertThat(details.getFirst().selectionMode()).isEqualTo("DIRECT");
        assertThat(details.getFirst().contractHash())
                .isEqualTo(ExplicitUserResolver.CONTRACT_HASH.value());
        assertThat(details.getFirst().status()).isEqualTo("ACTIVE");
    }

    @Test
    void selectionMustValidateTypeAndVersion() {
        assertThat(service.select(AssignmentStrategy.Type.USER,
                ExplicitUserResolver.RESOLVER_VERSION)).isSameAs(resolver);
        assertThatThrownBy(() -> service.select(AssignmentStrategy.Type.ROLE,
                ResolverVersion.of("ROLE_V1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("version mismatch");
    }
}
