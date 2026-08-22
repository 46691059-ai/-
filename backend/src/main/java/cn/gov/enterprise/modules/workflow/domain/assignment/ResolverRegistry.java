package cn.gov.enterprise.modules.workflow.domain.assignment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable startup registry. Runtime registration and mutation are intentionally unsupported. */
public final class ResolverRegistry {
    private final Map<ResolverKey, Registration> registrations;

    public ResolverRegistry(Collection<AssignmentResolver> resolvers) {
        this(resolvers.stream().map(AssignmentResolver::descriptor).toList(), resolvers);
    }

    public ResolverRegistry(
            Collection<AssignmentResolverDescriptor> descriptors,
            Collection<AssignmentResolver> resolvers) {
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(resolvers, "resolvers");
        Map<ResolverKey, AssignmentResolver> implementations = new HashMap<>();
        for (AssignmentResolver resolver : resolvers) {
            ResolverKey key = ResolverKey.of(resolver.descriptor());
            AssignmentResolver previous = implementations.put(key, resolver);
            if (previous != null) throw invalid("duplicate resolver implementation code");
        }
        Map<ResolverKey, Registration> indexed = new HashMap<>();
        for (AssignmentResolverDescriptor descriptor : descriptors) {
            ResolverKey key = ResolverKey.of(descriptor);
            AssignmentResolver resolver = implementations.get(key);
            if (resolver == null && descriptor.status() == ResolverStatus.PREPARED
                    && !descriptor.enabled()) {
                if (indexed.put(key, new Registration(descriptor, null)) != null) {
                    throw invalid("duplicate resolver code and version");
                }
                continue;
            }
            if (resolver == null || !sameContract(resolver.descriptor(), descriptor)) {
                throw invalid("resolver descriptor does not match implementation");
            }
            if (indexed.put(key, new Registration(descriptor, resolver)) != null) {
                throw invalid("duplicate resolver code and version");
            }
        }
        if (indexed.isEmpty()) throw invalid("resolver registry must not be empty");
        registrations = Map.copyOf(indexed);
    }

    public AssignmentResolver require(AssignmentStrategy.Type strategyType) {
        if (strategyType == null) throw new IllegalArgumentException("strategyType must not be null");
        List<Registration> candidates = registrations.values().stream()
                .filter(item -> item.descriptor().strategyType() == strategyType).toList();
        if (candidates.isEmpty()) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.NOT_FOUND,
                    "resolver is not registered for strategy " + strategyType);
        }
        List<Registration> matches = candidates.stream()
                .filter(item -> item.descriptor().enabled())
                .filter(item -> item.descriptor().status() == ResolverStatus.ACTIVE).toList();
        if (matches.isEmpty() && candidates.size() == 1) return requireActive(candidates.getFirst());
        if (matches.size() != 1) {
            throw invalid("strategy has multiple active resolver versions; exact routing is required");
        }
        return matches.getFirst().resolver();
    }

    public AssignmentResolver require(
            AssignmentStrategy.Type strategyType, ResolverVersion expectedVersion) {
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        List<Registration> matches = registrations.values().stream()
                .filter(item -> item.descriptor().strategyType() == strategyType)
                .filter(item -> item.descriptor().version().equals(expectedVersion)).toList();
        if (matches.isEmpty()) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.VERSION_MISMATCH,
                    "resolver version mismatch for strategy " + strategyType);
        }
        if (matches.size() != 1) throw invalid("resolver type and version are ambiguous");
        return requireActive(matches.getFirst());
    }

    public AssignmentResolver require(ResolverCode resolverCode) {
        Objects.requireNonNull(resolverCode, "resolverCode");
        List<Registration> candidates = registrations.values().stream()
                .filter(item -> item.descriptor().code().equals(resolverCode)).toList();
        if (candidates.isEmpty()) throw notFound(resolverCode);
        List<Registration> matches = candidates.stream()
                .filter(item -> item.descriptor().enabled())
                .filter(item -> item.descriptor().status() == ResolverStatus.ACTIVE).toList();
        if (matches.isEmpty() && candidates.size() == 1) return requireActive(candidates.getFirst());
        if (matches.size() != 1) {
            throw invalid("resolver code has multiple active versions; exact routing is required");
        }
        return matches.getFirst().resolver();
    }

    public AssignmentResolver require(
            ResolverCode resolverCode, ResolverVersion resolverVersion,
            ResolverContractHash expectedContractHash) {
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Objects.requireNonNull(expectedContractHash, "expectedContractHash");
        Registration registration = registrations.get(new ResolverKey(resolverCode, resolverVersion));
        if (registration == null) throw notFound(resolverCode);
        if (!registration.descriptor().contractHash().equals(expectedContractHash)) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.CONTRACT_HASH_MISMATCH,
                    "resolver contract hash mismatch for " + resolverCode.value());
        }
        return requireActive(registration);
    }

    public AssignmentResolver require(ResolverVersionBinding binding) {
        Objects.requireNonNull(binding, "binding");
        AssignmentResolver resolver = require(binding.resolverCode(), binding.resolverVersion(),
                binding.contractHash());
        return resolver;
    }

    /** Exact historical lookup: DEPRECATED/RETIRED remain executable, SECURITY_BLOCKED is not registered. */
    public AssignmentResolver requireForExistingInstance(ResolverVersionBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Registration registration = registrations.get(new ResolverKey(
                binding.resolverCode(), binding.resolverVersion()));
        if (registration == null) throw notFound(binding.resolverCode());
        if (!registration.descriptor().contractHash().equals(binding.contractHash())) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.CONTRACT_HASH_MISMATCH,
                    "resolver contract hash mismatch for " + binding.resolverCode().value());
        }
        if (!registration.descriptor().enabled()) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.DISABLED,
                    "resolver implementation is unavailable: " + binding.resolverCode().value());
        }
        if (registration.resolver() == null) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.DISABLED,
                    "resolver implementation is unavailable: "
                            + registration.descriptor().code().value());
        }
        return registration.resolver();
    }

    public List<AssignmentResolverDescriptor> descriptors() {
        return registrations.values().stream().map(Registration::descriptor)
                .sorted((left, right) -> left.strategyType().compareTo(right.strategyType()))
                .toList();
    }

    /** Metadata-only lookup. PREPARED descriptors are visible but remain non-executable. */
    public AssignmentResolverDescriptor requireDescriptor(
            ResolverCode resolverCode, ResolverVersion resolverVersion) {
        Objects.requireNonNull(resolverCode, "resolverCode");
        Objects.requireNonNull(resolverVersion, "resolverVersion");
        Registration registration = registrations.get(new ResolverKey(resolverCode, resolverVersion));
        if (registration == null) throw notFound(resolverCode);
        return registration.descriptor();
    }

    /** Exact metadata contract check without resolving an executable implementation. */
    public AssignmentResolverDescriptor requireDescriptor(
            ResolverCode resolverCode, ResolverVersion resolverVersion,
            ResolverStatus expectedStatus, ResolverContractHash expectedContractHash) {
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        Objects.requireNonNull(expectedContractHash, "expectedContractHash");
        AssignmentResolverDescriptor descriptor = requireDescriptor(resolverCode, resolverVersion);
        if (descriptor.status() != expectedStatus) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.STATUS_NOT_ACTIVE,
                    "resolver descriptor status mismatch: " + resolverCode.value());
        }
        if (!descriptor.contractHash().equals(expectedContractHash)) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.CONTRACT_HASH_MISMATCH,
                    "resolver descriptor contract hash mismatch: " + resolverCode.value());
        }
        return descriptor;
    }

    public static ResolverRegistry explicitUserOnly() {
        return new ResolverRegistry(List.of(new ExplicitUserResolver()));
    }

    private AssignmentResolverRegistryException invalid(String message) {
        return new AssignmentResolverRegistryException(
                AssignmentResolverRegistryException.Reason.INVALID_REGISTRATION, message);
    }

    private AssignmentResolver requireActive(Registration registration) {
        if (!registration.descriptor().enabled()) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.DISABLED,
                    "resolver is disabled: " + registration.descriptor().code().value());
        }
        if (registration.descriptor().status() != ResolverStatus.ACTIVE) {
            throw new AssignmentResolverRegistryException(
                    AssignmentResolverRegistryException.Reason.STATUS_NOT_ACTIVE,
                    "resolver status is not ACTIVE: " + registration.descriptor().code().value());
        }
        return registration.resolver();
    }

    private AssignmentResolverRegistryException notFound(ResolverCode code) {
        return new AssignmentResolverRegistryException(
                AssignmentResolverRegistryException.Reason.NOT_FOUND,
                "resolver code or version is not registered: " + code.value());
    }

    private boolean sameContract(
            AssignmentResolverDescriptor implementation,
            AssignmentResolverDescriptor registration) {
        return implementation.code().equals(registration.code())
                && implementation.version().equals(registration.version())
                && implementation.strategyType() == registration.strategyType()
                && implementation.mode() == registration.mode()
                && implementation.contractHash().equals(registration.contractHash());
    }

    private record Registration(
            AssignmentResolverDescriptor descriptor, AssignmentResolver resolver) { }

    private record ResolverKey(ResolverCode code, ResolverVersion version) {
        private static ResolverKey of(AssignmentResolverDescriptor descriptor) {
            return new ResolverKey(descriptor.code(), descriptor.version());
        }
    }
}
