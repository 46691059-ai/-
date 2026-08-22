package cn.gov.enterprise.modules.workflow.support;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeCapabilityResult;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityCapabilities;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeRoleMembershipResult;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeUserStatus;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeUserStatusResult;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly test-only capabilities. This class is not a Spring component and never calls production services. */
public final class FakeRealtimeEligibilityCapabilities {
    public final AtomicInteger roleDirectoryCalls = new AtomicInteger();
    public RealtimeRoleMembershipResult role;
    public RealtimeUserStatusResult user;
    public RealtimeCapabilityResult organization;
    public RealtimeCapabilityResult dataScope;
    public RealtimeCapabilityResult platformSoD;
    public RealtimeCapabilityResult businessSoD;
    public RealtimeCapabilityResult audit;
    public RealtimeCapabilityResult feature;
    public RealtimeCapabilityResult killSwitch;
    public RealtimeCapabilityResult canary;

    public FakeRealtimeEligibilityCapabilities(Instant validUntil, String contractHash) {
        role = new RealtimeRoleMembershipResult(RealtimeCapabilityResult.Outcome.PASS, true, false,
                "R15", hash("directory-result"), contractHash, "role membership active", hash("role"), validUntil);
        user = new RealtimeUserStatusResult(RealtimeUserStatus.ACTIVE, "TEST_STATUS_MAP_V1", hash("user"));
        organization = pass("organization", validUntil);
        dataScope = pass("data-scope", validUntil);
        platformSoD = pass("platform-sod", validUntil);
        businessSoD = pass("business-sod", validUntil);
        audit = pass("audit", validUntil);
        feature = pass("feature", validUntil);
        killSwitch = pass("kill-switch-clear", validUntil);
        canary = pass("canary", validUntil);
    }

    public RealtimeEligibilityCapabilities.Bundle bundle() {
        return new RealtimeEligibilityCapabilities.Bundle(
                query -> { roleDirectoryCalls.incrementAndGet(); return role; },
                query -> user,
                query -> organization,
                query -> dataScope,
                query -> platformSoD,
                query -> businessSoD,
                query -> audit,
                query -> feature,
                query -> killSwitch,
                query -> canary);
    }

    public static RealtimeCapabilityResult pass(String seed, Instant validUntil) {
        return new RealtimeCapabilityResult(RealtimeCapabilityResult.Outcome.PASS, seed + " passed",
                hash(seed), "TEST_POLICY_V1", validUntil);
    }

    public static RealtimeCapabilityResult deny(String seed) {
        return new RealtimeCapabilityResult(RealtimeCapabilityResult.Outcome.DENY, seed + " denied",
                hash(seed), "TEST_POLICY_V1", null);
    }

    public static RealtimeCapabilityResult indeterminate(String seed) {
        return new RealtimeCapabilityResult(RealtimeCapabilityResult.Outcome.INDETERMINATE, seed + " unavailable",
                hash(seed), "TEST_POLICY_V1", null);
    }

    public static String hash(String seed) { return RealtimeEligibilityCanonical.sha256(seed); }
}
