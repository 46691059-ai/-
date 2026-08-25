package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.repository.VersionNodeResolverBindingRepository;
import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowVersionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DRAFT-only application guard for Version resolver-binding mutation. */
@Service
public class VersionResolverBindingApplicationService {
    private final WorkflowVersionRepository versions;
    private final VersionNodeResolverBindingRepository bindings;

    public VersionResolverBindingApplicationService(
            WorkflowVersionRepository versions,
            VersionNodeResolverBindingRepository bindings) {
        this.versions = versions;
        this.bindings = bindings;
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public void add(VersionNodeResolverBinding binding) {
        requireEditable(binding.definitionId(), binding.definitionVersionId());
        bindings.add(binding);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public void update(VersionNodeResolverBinding binding) {
        VersionNodeResolverBinding existing = requireBinding(binding.id());
        requireSameOwner(binding, existing);
        requireEditable(existing.definitionId(), existing.definitionVersionId());
        bindings.update(binding);
    }

    @Transactional
    @PreAuthorize("hasAuthority('workflow:definition:edit')")
    public void delete(Long definitionId, Long definitionVersionId,
            Long bindingId, int expectedVersion) {
        requireEditable(definitionId, definitionVersionId);
        VersionNodeResolverBinding binding = requireBinding(bindingId);
        if (!definitionId.equals(binding.definitionId())
                || !definitionVersionId.equals(binding.definitionVersionId())) {
            throw new BusinessException("B2500", "Version resolver binding ownership mismatch");
        }
        if (!bindings.logicalDelete(bindingId, expectedVersion)) {
            throw new BusinessException("B2629", "Version resolver binding changed concurrently");
        }
    }

    private WorkflowVersion requireEditable(Long definitionId, Long definitionVersionId) {
        WorkflowVersion version = versions.findByIdForUpdate(definitionVersionId)
                .orElseThrow(() -> new BusinessException("B2504", "workflow version does not exist"));
        if (!definitionId.equals(version.definitionId())) {
            throw new BusinessException("B2500", "workflow version belongs to another definition");
        }
        if (!version.isEditable()) {
            throw new BusinessException("B2503", "published or retired resolver bindings are immutable");
        }
        return version;
    }

    private VersionNodeResolverBinding requireBinding(Long bindingId) {
        return bindings.findById(bindingId)
                .orElseThrow(() -> new BusinessException(
                        "B2504", "Version resolver binding does not exist"));
    }

    private void requireSameOwner(
            VersionNodeResolverBinding requested, VersionNodeResolverBinding existing) {
        if (!requested.definitionId().equals(existing.definitionId())
                || !requested.definitionVersionId().equals(existing.definitionVersionId())
                || !requested.nodeId().equals(existing.nodeId())) {
            throw new BusinessException("B2500", "Version resolver binding ownership mismatch");
        }
    }
}
