package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.List;
import java.util.Objects;

/** The only admissible production assembly for the ten realtime eligibility capabilities. */
public final class RoleRuntimeProductionCapabilityBundle {
    public static final String CONTRACT_VERSION = "ROLE_RUNTIME_PRODUCTION_CAPABILITY_BUNDLE_V1";
    private final RealtimeEligibilityCapabilities.Bundle delegate;

    public RoleRuntimeProductionCapabilityBundle(RealtimeEligibilityCapabilities.Bundle delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        List<Object> ports = List.of(delegate.roleMembership(), delegate.userStatus(),
                delegate.organizationMembership(), delegate.dataScope(), delegate.platformSoD(),
                delegate.businessSoD(), delegate.audit(), delegate.featureFlag(),
                delegate.killSwitch(), delegate.canary());
        for (Object port : ports) {
            String name = port.getClass().getName().toLowerCase();
            if (name.contains("fake") || name.contains("stub") || name.contains("inmemory")) {
                throw new IllegalArgumentException("non-production capability in production bundle: "
                        + port.getClass().getName());
            }
        }
    }

    public RealtimeEligibilityCapabilities.Bundle capabilities() { return delegate; }
}
