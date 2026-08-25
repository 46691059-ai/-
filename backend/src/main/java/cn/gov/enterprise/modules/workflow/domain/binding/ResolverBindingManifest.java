package cn.gov.enterprise.modules.workflow.domain.binding;

import java.time.LocalDateTime;
import java.util.Objects;

/** Append-only release evidence for one Version resolver-binding collection. */
public record ResolverBindingManifest(
        Long id,
        Long definitionId,
        Long definitionVersionId,
        String canonicalVersion,
        int bindingCount,
        String manifestHash,
        Long releasedBy,
        LocalDateTime releasedTime) {
    public static final String CANONICAL_VERSION = "VERSION_RESOLVER_BINDING_MANIFEST_V1";

    public ResolverBindingManifest {
        positive(id, "id");
        positive(definitionId, "definitionId");
        positive(definitionVersionId, "definitionVersionId");
        if (!CANONICAL_VERSION.equals(canonicalVersion)) {
            throw new IllegalArgumentException("unsupported manifest canonicalVersion");
        }
        if (bindingCount < 1) throw new IllegalArgumentException("bindingCount must be positive");
        manifestHash = VersionNodeResolverBinding.lowercaseSha256(manifestHash, "manifestHash");
        positive(releasedBy, "releasedBy");
        Objects.requireNonNull(releasedTime, "releasedTime");
    }

    private static void positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
