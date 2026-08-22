package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.util.Objects;

/** First release requires both enterprise and definition-version gates. */
public record RoleRuntimeFeatureFlagPolicy(
        Scope scope, String policyVersion, RoleRuntimeCanaryScope allowedScope) {
    public enum Scope { GLOBAL, ENTERPRISE, DEFINITION_VERSION }

    public RoleRuntimeFeatureFlagPolicy {
        Objects.requireNonNull(scope, "scope");
        policyVersion = RoleRuntimeExecutionAdmissionRequest.text(
                policyVersion, "policyVersion", 64);
        Objects.requireNonNull(allowedScope, "allowedScope");
    }

    public String canonicalValue() {
        return scope.name() + "|" + policyVersion + "|" + allowedScope.canonicalValue();
    }
}
