package cn.gov.enterprise.modules.workflow.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.service.VersionResolverBindingApplicationService;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VersionResolverBindingApplicationServiceTest {
    private final WorkflowVersionRepository versions = mock(WorkflowVersionRepository.class);
    private final VersionNodeResolverBindingRepository bindings =
            mock(VersionNodeResolverBindingRepository.class);
    private final VersionResolverBindingApplicationService service =
            new VersionResolverBindingApplicationService(versions, bindings);

    @Test
    void draftBindingMutationShouldRemainAvailable() {
        VersionNodeResolverBinding binding = binding();
        when(versions.findByIdForUpdate(20L)).thenReturn(Optional.of(WorkflowVersion.draft(
                20L, 1L, 1, "2.0", null, null)));
        when(bindings.findById(binding.id())).thenReturn(Optional.of(binding));
        when(bindings.logicalDelete(binding.id(), 0)).thenReturn(true);

        service.add(binding);
        service.update(binding);
        service.delete(1L, 20L, binding.id(), 0);

        verify(bindings).add(binding);
        verify(bindings).update(binding);
        verify(bindings).logicalDelete(binding.id(), 0);
    }

    @Test
    void publishedAndRetiredBindingMutationMustFailAtApplicationBoundary() {
        VersionNodeResolverBinding binding = binding();
        when(versions.findByIdForUpdate(20L)).thenReturn(Optional.of(published()));
        when(bindings.findById(binding.id())).thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> service.add(binding))
                .isInstanceOf(BusinessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> service.update(binding))
                .isInstanceOf(BusinessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> service.delete(1L, 20L, binding.id(), 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("immutable");
        verify(bindings, never()).add(binding);
        verify(bindings, never()).update(binding);
        verify(bindings, never()).logicalDelete(binding.id(), 0);
    }

    private WorkflowVersion published() {
        LocalDateTime now = LocalDateTime.now();
        return new WorkflowVersion(20L, 1L, 1, WorkflowVersion.Status.PUBLISHED,
                "2.0", "a".repeat(64), null, now, null, 7L, now, null, 0);
    }

    private VersionNodeResolverBinding binding() {
        return new VersionNodeResolverBinding(
                1L, 1L, 20L, 30L, 1, ResolverCode.of("ROLE_DIRECTORY"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of("a".repeat(64)), AssignmentStrategy.Type.ROLE,
                ResolverMode.CANDIDATE_POOL, AssignmentStrategy.Type.ROLE, "ROLE_APPROVER",
                OrganizationScopeType.FIXED_ORG, 8L,
                EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "b".repeat(64), 0);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
