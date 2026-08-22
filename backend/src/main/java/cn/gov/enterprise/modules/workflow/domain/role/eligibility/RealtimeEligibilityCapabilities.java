package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.util.Objects;

/** Stable capability ports. Production assembly is provided by RoleRuntimeProductionCapabilityBundle. */
public final class RealtimeEligibilityCapabilities {
    private RealtimeEligibilityCapabilities() { }

    @FunctionalInterface
    public interface RealtimeRoleMembershipPort {
        RealtimeRoleMembershipResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeUserStatusPort {
        RealtimeUserStatusResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeOrganizationMembershipPort {
        RealtimeCapabilityResult checkExactBusinessOrganization(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RoleRuntimeDataScopePort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface PlatformSoDPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface BusinessSoDPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeAuditCapabilityPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeFeatureFlagPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeKillSwitchPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    @FunctionalInterface
    public interface RealtimeCanaryPort {
        RealtimeCapabilityResult check(RealtimeEligibilityQuery query);
    }

    public record Bundle(
            RealtimeRoleMembershipPort roleMembership,
            RealtimeUserStatusPort userStatus,
            RealtimeOrganizationMembershipPort organizationMembership,
            RoleRuntimeDataScopePort dataScope,
            PlatformSoDPort platformSoD,
            BusinessSoDPort businessSoD,
            RealtimeAuditCapabilityPort audit,
            RealtimeFeatureFlagPort featureFlag,
            RealtimeKillSwitchPort killSwitch,
            RealtimeCanaryPort canary) {
        public Bundle {
            Objects.requireNonNull(roleMembership, "roleMembership");
            Objects.requireNonNull(userStatus, "userStatus");
            Objects.requireNonNull(organizationMembership, "organizationMembership");
            Objects.requireNonNull(dataScope, "dataScope");
            Objects.requireNonNull(platformSoD, "platformSoD");
            Objects.requireNonNull(businessSoD, "businessSoD");
            Objects.requireNonNull(audit, "audit");
            Objects.requireNonNull(featureFlag, "featureFlag");
            Objects.requireNonNull(killSwitch, "killSwitch");
            Objects.requireNonNull(canary, "canary");
        }
    }
}
