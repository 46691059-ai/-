package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeCapabilityResult.Outcome;
import static cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityFailureCode.*;

/** Fixed-order ROLE eligibility validator. It creates evidence only and never mutates runtime state. */
public final class RealtimeEligibilityValidator implements RealtimeEligibilityPort {
    @Override
    public RealtimeEligibilityResult assess(RealtimeEligibilityContext context) {
        Objects.requireNonNull(context, "context");
        Working w = new Working(context);

        // Candidate preflight is deliberately local. No Directory capability is called for a non-candidate.
        boolean frozenCandidate = context.facts().frozenCandidateUserIds().contains(context.query().candidateUserId());

        if (!w.local(1, "ROLE_RUNTIME_GATE", context.facts().roleRuntimeGateOpen(),
                ROLE_RUNTIME_DISABLED, "ROLE runtime gate is closed")) return w.finish();
        w.killSwitch = w.call(2, "KILL_SWITCH", CAPABILITY_UNAVAILABLE, "kill switch unavailable",
                () -> context.capabilities().killSwitch().check(context.query()));
        if (w.killSwitch == null) return w.finish();
        if (!w.capability(2, "KILL_SWITCH", w.killSwitch, KILL_SWITCH_ACTIVE,
                CAPABILITY_UNAVAILABLE, "kill switch")) return w.finish();
        if (!w.local(3, "TASK_EXISTS", context.facts().taskExists(), TASK_NOT_FOUND, "task does not exist")) return w.finish();
        if (!w.local(4, "TASK_CLAIMABLE", context.facts().taskClaimable(), TASK_NOT_CLAIMABLE, "task is not claimable")) return w.finish();
        if (!w.local(5, "CANDIDATE_POOL_EXISTS", context.facts().candidatePoolExists(), CANDIDATE_POOL_NOT_FOUND, "candidate pool does not exist")) return w.finish();
        if (!w.local(6, "POOL_BELONGS_TASK", context.facts().poolTaskId() == context.query().taskId(), CANDIDATE_POOL_MISMATCH, "pool does not belong to task")) return w.finish();
        if (!w.local(7, "CANDIDATE_IN_POOL", frozenCandidate, NOT_CANDIDATE, "user is not in frozen candidate pool")) return w.finish();
        if (!w.local(8, "CANDIDATE_MEMBER_ACTIVE", context.facts().candidateMemberActive(), CANDIDATE_MEMBER_INACTIVE, "candidate member is inactive")) return w.finish();
        if (!w.local(9, "RUNTIME_BINDING_MATCH", context.query().runtimeBindingHash().equals(context.facts().boundRuntimeBindingHash()), BINDING_MISMATCH, "runtime binding hash mismatch")) return w.finish();
        if (!w.local(10, "ROLE_MATCH", context.query().roleCode().equals(context.facts().boundRoleCode()), BINDING_MISMATCH, "role code mismatch")) return w.finish();
        if (!w.local(11, "ORGANIZATION_MATCH", context.query().organizationId().equals(context.facts().boundOrganizationId()), BINDING_MISMATCH, "organization mismatch")) return w.finish();
        if (!w.local(12, "ENTERPRISE_MATCH", context.query().enterpriseId().equals(context.facts().boundEnterpriseId()), ENTERPRISE_MISMATCH, "enterprise mismatch")) return w.finish();

        if (context.facts().directoryCircuitOpen()) {
            w.fail(13, "USER_CURRENT_ACTIVE", DIRECTORY_UNAVAILABLE, "directory circuit is open");
            return w.finish();
        }
        w.user = w.call(13, "USER_CURRENT_ACTIVE", DIRECTORY_UNAVAILABLE, "user directory unavailable",
                () -> context.capabilities().userStatus().check(context.query()));
        if (w.user == null) return w.finish();
        if (w.user.status() == RealtimeUserStatus.UNKNOWN) {
            w.fail(13, "USER_CURRENT_ACTIVE", DIRECTORY_UNAVAILABLE, "user status is unknown"); return w.finish();
        }
        if (!w.local(13, "USER_CURRENT_ACTIVE", w.user.status() == RealtimeUserStatus.ACTIVE,
                USER_INACTIVE, "user is not active")) return w.finish();

        w.organization = w.call(14, "CURRENT_ORG_MEMBERSHIP", DIRECTORY_UNAVAILABLE,
                "organization directory unavailable",
                () -> context.capabilities().organizationMembership().checkExactBusinessOrganization(context.query()));
        if (w.organization == null) return w.finish();
        if (!w.capability(14, "CURRENT_ORG_MEMBERSHIP", w.organization, ORG_MEMBERSHIP_LOST,
                DIRECTORY_UNAVAILABLE, "organization membership")) return w.finish();

        w.role = w.call(15, "CURRENT_ROLE_MEMBERSHIP", DIRECTORY_UNAVAILABLE, "role directory unavailable",
                () -> context.capabilities().roleMembership().check(context.query()));
        if (w.role == null) return w.finish();
        if (!w.roleCapability(15)) return w.finish();
        if (!w.directoryComplete(16)) return w.finish();
        boolean revisionValid = w.role.claimRevision() != null
                && w.role.contractHash().equals(context.facts().directoryContractHash());
        if (!w.localIndeterminate(17, "DIRECTORY_REVISION_VALID", revisionValid, REVISION_INVALID,
                "directory revision fence is invalid")) return w.finish();

        w.dataScope = w.call(18, "DATASCOPE", DATASCOPE_UNAVAILABLE, "data scope unavailable",
                () -> context.capabilities().dataScope().check(context.query()));
        if (w.dataScope == null) return w.finish();
        if (!w.capability(18, "DATASCOPE", w.dataScope, DATASCOPE_DENIED, DATASCOPE_UNAVAILABLE, "data scope")) return w.finish();
        w.platformSoD = w.call(19, "PLATFORM_SOD", CAPABILITY_UNAVAILABLE, "platform SoD unavailable",
                () -> context.capabilities().platformSoD().check(context.query()));
        if (w.platformSoD == null) return w.finish();
        if (!w.capability(19, "PLATFORM_SOD", w.platformSoD, PLATFORM_SOD_DENIED, CAPABILITY_UNAVAILABLE, "platform SoD")) return w.finish();
        w.businessSoD = w.call(20, "BUSINESS_SOD", CAPABILITY_UNAVAILABLE, "business SoD unavailable",
                () -> context.capabilities().businessSoD().check(context.query()));
        if (w.businessSoD == null) return w.finish();
        if (!w.capability(20, "BUSINESS_SOD", w.businessSoD, BUSINESS_SOD_DENIED, CAPABILITY_UNAVAILABLE, "business SoD")) return w.finish();
        if (!w.local(21, "EXISTING_ACTIVE_CLAIM", !context.facts().activeClaimExists(), ACTIVE_CLAIM_EXISTS, "active claim already exists")) return w.finish();
        if (!w.local(22, "TASK_ASSIGNEE", context.facts().taskAssigneeCompatible(), ASSIGNEE_MISMATCH, "task assignee conflicts")) return w.finish();
        if (!w.local(23, "CLAIM_IDEMPOTENCY", context.facts().idempotencyCompatible(), IDEMPOTENCY_CONFLICT, "idempotency key conflicts")) return w.finish();
        w.audit = w.call(24, "AUDIT_CAPABILITY", AUDIT_UNAVAILABLE, "audit unavailable",
                () -> context.capabilities().audit().check(context.query()));
        if (w.audit == null) return w.finish();
        if (!w.capability(24, "AUDIT_CAPABILITY", w.audit, AUDIT_UNAVAILABLE, AUDIT_UNAVAILABLE, "audit capability")) return w.finish();
        w.feature = w.call(25, "FEATURE_FLAG", CAPABILITY_UNAVAILABLE, "feature flag unavailable",
                () -> context.capabilities().featureFlag().check(context.query()));
        if (w.feature == null) return w.finish();
        if (!w.capability(25, "FEATURE_FLAG", w.feature, FEATURE_DISABLED, CAPABILITY_UNAVAILABLE, "feature flag")) return w.finish();
        w.canary = w.call(26, "CANARY", CAPABILITY_UNAVAILABLE, "canary unavailable",
                () -> context.capabilities().canary().check(context.query()));
        if (w.canary == null) return w.finish();
        if (!w.capability(26, "CANARY", w.canary, CANARY_DENIED, CAPABILITY_UNAVAILABLE, "canary")) return w.finish();
        w.pass(27, "FINAL_ELIGIBILITY_HASH", "canonical eligibility evidence generated", context.query().correlationId());
        return w.finish();
    }

    private static final class Working {
        private final RealtimeEligibilityContext context;
        private final List<RealtimeEligibilityValidationResult> checks = new ArrayList<>();
        private RealtimeEligibilityFailure failure;
        private RealtimeUserStatusResult user;
        private RealtimeCapabilityResult organization;
        private RealtimeRoleMembershipResult role;
        private RealtimeCapabilityResult dataScope;
        private RealtimeCapabilityResult platformSoD;
        private RealtimeCapabilityResult businessSoD;
        private RealtimeCapabilityResult feature;
        private RealtimeCapabilityResult canary;
        private RealtimeCapabilityResult killSwitch;
        private RealtimeCapabilityResult audit;

        private Working(RealtimeEligibilityContext context) { this.context = context; }

        boolean local(int order, String code, boolean passed, RealtimeEligibilityFailureCode failCode, String reason) {
            if (passed) { pass(order, code, "passed", true); return true; }
            fail(order, code, failCode, reason); return false;
        }

        boolean localIndeterminate(int order, String code, boolean passed, RealtimeEligibilityFailureCode failCode, String reason) {
            if (passed) { pass(order, code, "passed", true); return true; }
            fail(order, code, failCode, reason); return false;
        }

        boolean capability(int order, String code, RealtimeCapabilityResult result,
                           RealtimeEligibilityFailureCode denied, RealtimeEligibilityFailureCode indeterminate,
                           String label) {
            if (result.outcome() == Outcome.PASS) { pass(order, code, result.reason(), result.evidenceHash()); return true; }
            fail(order, code, result.outcome() == Outcome.DENY ? denied : indeterminate,
                    label + ": " + result.reason());
            return false;
        }

        boolean roleCapability(int order) {
            if (role.outcome() == Outcome.PASS) { pass(order, "CURRENT_ROLE_MEMBERSHIP", role.reason(), role.evidenceHash()); return true; }
            fail(order, "CURRENT_ROLE_MEMBERSHIP",
                    role.outcome() == Outcome.DENY ? ROLE_MEMBERSHIP_LOST : DIRECTORY_UNAVAILABLE,
                    "role membership: " + role.reason());
            return false;
        }

        <T> T call(int order, String code, RealtimeEligibilityFailureCode failureCode,
                   String reason, Supplier<T> supplier) {
            try {
                T value = supplier.get();
                if (value == null) throw new IllegalStateException("capability returned null");
                return value;
            } catch (RuntimeException ex) {
                fail(order, code, failureCode, reason);
                return null;
            }
        }

        boolean directoryComplete(int order) {
            if (role.sourceConflict()) {
                fail(order, "DIRECTORY_COMPLETE", DIRECTORY_CONFLICT, "directory sources conflict");
                return false;
            }
            if (!role.complete()) {
                fail(order, "DIRECTORY_COMPLETE", DIRECTORY_PARTIAL, "directory result is incomplete");
                return false;
            }
            pass(order, "DIRECTORY_COMPLETE", "directory result is complete", role.directoryResultHash());
            return true;
        }

        void pass(int order, String code, String reason, Object fact) {
            checks.add(new RealtimeEligibilityValidationResult(code, order,
                    RealtimeEligibilityValidationResult.Status.PASS, reason,
                    fact instanceof String hash && hash.matches("[0-9a-f]{64}") ? hash
                            : RealtimeEligibilityCanonical.validatorHash(code, reason, fact),
                    context.query().claimAt()));
        }

        void fail(int order, String code, RealtimeEligibilityFailureCode failureCode, String reason) {
            failure = new RealtimeEligibilityFailure(failureCode, reason);
            RealtimeEligibilityValidationResult.Status validationStatus = failure.status() == RealtimeEligibilityStatus.INELIGIBLE
                    ? RealtimeEligibilityValidationResult.Status.FAIL : RealtimeEligibilityValidationResult.Status.INDETERMINATE;
            checks.add(new RealtimeEligibilityValidationResult(code, order, validationStatus, reason,
                    RealtimeEligibilityCanonical.validatorHash(code, reason, failureCode), context.query().claimAt()));
        }

        RealtimeEligibilityResult finish() {
            RealtimeEligibilityStatus status = failure == null ? RealtimeEligibilityStatus.ELIGIBLE : failure.status();
            Instant verifiedAt = context.query().claimAt();
            Instant expiresAt = verifiedAt.plus(context.policy().maxEligibilityAge());
            if (role != null && role.validUntil() != null && role.validUntil().isBefore(expiresAt)) expiresAt = role.validUntil();
            if (!expiresAt.isAfter(verifiedAt)) {
                failure = new RealtimeEligibilityFailure(EVIDENCE_EXPIRED, "capability evidence is already expired");
                status = failure.status();
                expiresAt = verifiedAt.plusNanos(1);
            }
            String evidenceHash = RealtimeEligibilityCanonical.evidenceHash(context.query(), status, verifiedAt, expiresAt,
                    user, organization, role, dataScope, platformSoD, businessSoD, feature, canary, killSwitch, audit, checks);
            RealtimeEligibilityEvidence evidence = new RealtimeEligibilityEvidence(
                    context.query().candidateUserId(), context.query().candidatePoolHash(),
                    context.query().runtimeBindingHash(), context.query().roleCode(), context.query().organizationId(),
                    context.query().candidateDirectoryRevision(), role == null ? null : role.claimRevision(),
                    role == null ? null : role.directoryResultHash(), role == null ? null : role.contractHash(),
                    user, organization, role, dataScope, platformSoD, businessSoD, feature, canary, killSwitch, audit,
                    checks, verifiedAt, expiresAt, context.query().businessScopeReference(), context.query().correlationId(),
                    RealtimeEligibilityCanonical.VERSION, evidenceHash);
            RealtimeEligibilityDecision decision = new RealtimeEligibilityDecision(status,
                    status == RealtimeEligibilityStatus.ELIGIBLE, failure);
            String reasonCode = failure == null ? "ELIGIBLE" : failure.code().name();
            RealtimeEligibilityAuditEvidence auditEvidence = new RealtimeEligibilityAuditEvidence(
                    context.query().taskId(), context.query().candidateUserId(), status, reasonCode,
                    context.query().candidateDirectoryRevision(), role == null ? null : role.claimRevision(),
                    evidenceHash, verifiedAt, expiresAt, context.query().correlationId());
            return new RealtimeEligibilityResult(decision, evidence, auditEvidence);
        }
    }
}
