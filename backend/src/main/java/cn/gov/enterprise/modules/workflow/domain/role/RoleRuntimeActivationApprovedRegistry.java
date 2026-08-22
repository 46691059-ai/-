package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory approved evidence registry. Registration never enables a Resolver. */
public final class RoleRuntimeActivationApprovedRegistry {
    private final Map<String, RoleRuntimeActivationSnapshot> approved = new HashMap<>();

    public synchronized void register(RoleRuntimeActivationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (approved.putIfAbsent(snapshot.activationHash(), snapshot) != null) {
            throw new IllegalStateException("duplicate Activation approval");
        }
    }

    public synchronized Optional<RoleRuntimeActivationSnapshot> find(String activationHash) {
        return Optional.ofNullable(approved.get(
                RoleCandidateResult.hash(activationHash, "activationHash")));
    }

    public synchronized boolean approved(String activationHash) {
        return find(activationHash).isPresent();
    }
}
