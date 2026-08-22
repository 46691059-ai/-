package cn.gov.enterprise.modules.workflow.support;

import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeCanaryScope;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeCapabilityReadiness;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeCapabilityResult;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionRequest;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.AuditCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.CanaryScopePort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.DataScopeCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.DirectoryCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.FeatureFlagCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.KillSwitchCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.RealtimeEligibilityCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionCapabilityPorts.SoDCapabilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeFeatureFlagPolicy;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeKillSwitch;

/** Test-only fake adapters. They never connect to production services. */
public final class FakeRoleRuntimeExecutionCapabilityAdapters {
    private FakeRoleRuntimeExecutionCapabilityAdapters() { }

    private abstract static class FakeCapability {
        private final String name;
        private RoleRuntimeCapabilityReadiness readiness = RoleRuntimeCapabilityReadiness.READY;

        FakeCapability(String name) { this.name = name; }

        public void readiness(RoleRuntimeCapabilityReadiness value) { readiness = value; }

        RoleRuntimeCapabilityResult result() {
            return new RoleRuntimeCapabilityResult(name, readiness, "FAKE_" + readiness.name());
        }
    }

    public static final class FakeProductionDirectoryCapabilityAdapter extends FakeCapability
            implements DirectoryCapabilityPort {
        public FakeProductionDirectoryCapabilityAdapter() { super("DIRECTORY"); }
        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            return result();
        }
    }

    public static final class FakeRealtimeEligibilityCapabilityAdapter extends FakeCapability
            implements RealtimeEligibilityCapabilityPort {
        public FakeRealtimeEligibilityCapabilityAdapter() { super("REALTIME_ELIGIBILITY"); }
        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            return result();
        }
    }

    public static final class FakeDataScopeCapabilityAdapter extends FakeCapability
            implements DataScopeCapabilityPort {
        public FakeDataScopeCapabilityAdapter() { super("DATA_SCOPE"); }
        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            return result();
        }
    }

    public static final class FakeSoDCapabilityAdapter extends FakeCapability
            implements SoDCapabilityPort {
        public FakeSoDCapabilityAdapter() { super("SOD"); }
        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            return result();
        }
    }

    public static final class FakeAuditCapabilityAdapter extends FakeCapability
            implements AuditCapabilityPort {
        public FakeAuditCapabilityAdapter() { super("AUDIT"); }
        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            return result();
        }
    }

    public static final class FakeFeatureFlagCapabilityAdapter extends FakeCapability
            implements FeatureFlagCapabilityPort {
        private RoleRuntimeCanaryScope allowedScope;

        public FakeFeatureFlagCapabilityAdapter(RoleRuntimeCanaryScope allowedScope) {
            super("FEATURE_FLAG");
            this.allowedScope = allowedScope;
        }

        public void allowedScope(RoleRuntimeCanaryScope value) { allowedScope = value; }

        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            RoleRuntimeCapabilityResult base = result();
            if (!base.ready()) return base;
            boolean allowed = request.featureFlagPolicy().scope()
                    == RoleRuntimeFeatureFlagPolicy.Scope.DEFINITION_VERSION
                    && request.featureFlagPolicy().allowedScope().equals(allowedScope)
                    && request.canaryScope().equals(allowedScope);
            return new RoleRuntimeCapabilityResult("FEATURE_FLAG",
                    allowed ? RoleRuntimeCapabilityReadiness.READY
                            : RoleRuntimeCapabilityReadiness.BLOCKED,
                    allowed ? "FAKE_ENTERPRISE_DEFINITION_ENABLED" : "FAKE_SCOPE_DISABLED");
        }
    }

    public static final class FakeKillSwitchCapabilityAdapter extends FakeCapability
            implements KillSwitchCapabilityPort {
        private RoleRuntimeKillSwitch killSwitch = new RoleRuntimeKillSwitch(
                RoleRuntimeKillSwitch.Status.OPEN, "FAKE_V1");

        public FakeKillSwitchCapabilityAdapter() { super("KILL_SWITCH"); }
        public void killSwitch(RoleRuntimeKillSwitch value) { killSwitch = value; }

        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            RoleRuntimeCapabilityResult base = result();
            if (!base.ready()) return base;
            return new RoleRuntimeCapabilityResult("KILL_SWITCH",
                    killSwitch.allowsNewAdmission() ? RoleRuntimeCapabilityReadiness.READY
                            : RoleRuntimeCapabilityReadiness.BLOCKED,
                    "FAKE_KILL_SWITCH_" + killSwitch.status());
        }
    }

    public static final class FakeCanaryScopeAdapter extends FakeCapability
            implements CanaryScopePort {
        private RoleRuntimeCanaryScope allowedScope;

        public FakeCanaryScopeAdapter(RoleRuntimeCanaryScope allowedScope) {
            super("CANARY_SCOPE");
            this.allowedScope = allowedScope;
        }

        public void allowedScope(RoleRuntimeCanaryScope value) { allowedScope = value; }

        @Override public RoleRuntimeCapabilityResult assess(RoleRuntimeExecutionAdmissionRequest request) {
            RoleRuntimeCapabilityResult base = result();
            if (!base.ready()) return base;
            boolean allowed = request.canaryScope().equals(allowedScope);
            return new RoleRuntimeCapabilityResult("CANARY_SCOPE",
                    allowed ? RoleRuntimeCapabilityReadiness.READY
                            : RoleRuntimeCapabilityReadiness.BLOCKED,
                    allowed ? "FAKE_CANARY_ALLOWED" : "FAKE_CANARY_DENIED");
        }
    }
}
