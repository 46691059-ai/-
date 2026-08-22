package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.time.LocalDateTime;
import java.util.Objects;

/** Exact resolver implementation contract contained by a binding set. */
public record WorkflowResolverBinding(
        Long id, Long bindingSetId, Long instanceId, Long definitionVersionId,
        ResolverCode resolverCode, ResolverVersion resolverVersion,
        AssignmentStrategy.Type strategyType, ResolverMode resolverMode,
        ResolverContractHash contractHash, String ruleHash,
        WorkflowResolverBindingSet.Status status, LocalDateTime frozenTime,
        String auditInfo, int version) {

    public WorkflowResolverBinding {
        positive(id, "id"); positive(bindingSetId, "bindingSetId"); positive(instanceId, "instanceId");
        positive(definitionVersionId, "definitionVersionId");
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(strategyType, "strategyType");
        Objects.requireNonNull(resolverMode, "resolverMode");
        Objects.requireNonNull(contractHash, "contractHash");
        WorkflowResolverBindingSet.sha256(ruleHash, "ruleHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(frozenTime, "frozenTime");
        auditInfo = WorkflowResolverBindingSet.required(auditInfo, "auditInfo", 65535);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public ResolverVersionBinding asLegacyBinding() {
        return new ResolverVersionBinding(instanceId, resolverCode, resolverVersion, contractHash);
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
}
