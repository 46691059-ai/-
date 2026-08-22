package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.application.vo.AssignmentResolverDetail;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersionBinding;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Application boundary for resolver discovery. Registry mutation is intentionally absent. */
@Service
public class WorkflowAssignmentResolverApplicationService {
    private final ResolverRegistry registry;

    public WorkflowAssignmentResolverApplicationService(ResolverRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public AssignmentResolver select(
            AssignmentStrategy.Type strategyType, ResolverVersion expectedVersion) {
        try {
            return registry.require(strategyType, expectedVersion);
        } catch (AssignmentResolverRegistryException | IllegalArgumentException exception) {
            BusinessException businessException = new BusinessException("B2630", exception.getMessage());
            businessException.initCause(exception);
            throw businessException;
        }
    }

    public AssignmentResolver select(ResolverVersionBinding binding) {
        try {
            return registry.require(binding);
        } catch (AssignmentResolverRegistryException | IllegalArgumentException exception) {
            throw resolverFailure(exception);
        }
    }

    public AssignmentResolver selectForExistingInstance(ResolverVersionBinding binding) {
        try {
            return registry.requireForExistingInstance(binding);
        } catch (AssignmentResolverRegistryException | IllegalArgumentException exception) {
            throw resolverFailure(exception);
        }
    }

    public ResolverVersionBinding freeze(
            Long instanceId, ResolverCode resolverCode, ResolverVersion resolverVersion,
            ResolverContractHash contractHash) {
        AssignmentResolver resolver;
        try {
            resolver = registry.require(resolverCode, resolverVersion, contractHash);
            return ResolverVersionBinding.freeze(instanceId, resolver.descriptor());
        } catch (AssignmentResolverRegistryException | IllegalArgumentException
                | IllegalStateException exception) {
            throw resolverFailure(exception);
        }
    }

    @PreAuthorize("hasAuthority('workflow:view')")
    public List<AssignmentResolverDetail> listAvailable() {
        return registry.descriptors().stream().filter(descriptor -> descriptor.enabled())
                .map(AssignmentResolverDetail::from).toList();
    }

    private BusinessException resolverFailure(RuntimeException exception) {
        BusinessException businessException = new BusinessException("B2630", exception.getMessage());
        businessException.initCause(exception);
        return businessException;
    }
}
