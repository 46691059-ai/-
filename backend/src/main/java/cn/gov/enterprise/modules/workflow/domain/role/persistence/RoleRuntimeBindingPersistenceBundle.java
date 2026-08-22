package cn.gov.enterprise.modules.workflow.domain.role.persistence;

import java.util.List;
import java.util.Objects;

/** Atomic persistence payload; contains no Workflow runtime object. */
public record RoleRuntimeBindingPersistenceBundle(
        RoleRuntimeBindingPromotion promotion,
        RoleRuntimeBindingCandidateSnapshot snapshot,
        RoleRuntimeBindingEvidence evidence,
        List<RoleRuntimeBindingLifecycleEvent> lifecycleEvents) {
    public RoleRuntimeBindingPersistenceBundle {
        Objects.requireNonNull(promotion, "promotion");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(evidence, "evidence");
        lifecycleEvents = List.copyOf(Objects.requireNonNull(lifecycleEvents, "lifecycleEvents"));
        if (lifecycleEvents.size() != 2
                || lifecycleEvents.getFirst().toStatus() != RoleRuntimeBindingLifecycleStatus.CREATED
                || lifecycleEvents.getLast().toStatus() != RoleRuntimeBindingLifecycleStatus.VALIDATED) {
            throw new IllegalArgumentException("initial persistence must append CREATED and VALIDATED");
        }
    }
}
