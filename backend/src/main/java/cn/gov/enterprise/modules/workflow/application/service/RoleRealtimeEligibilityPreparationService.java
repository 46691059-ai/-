package cn.gov.enterprise.modules.workflow.application.service;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityContext;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityFailureCode;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityPort;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityResult;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityRevisionFence;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityStatus;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityVerificationFacts;
import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityVerificationToken;
import java.util.Objects;
import java.util.Optional;

/**
 * Non-executable, in-memory Prepare/Verify orchestration.
 * It is intentionally not a Spring bean and has no Task/Pool/Claim writer dependency.
 */
public final class RoleRealtimeEligibilityPreparationService {
    private final RealtimeEligibilityPort eligibilityPort;

    public RoleRealtimeEligibilityPreparationService(RealtimeEligibilityPort eligibilityPort) {
        this.eligibilityPort = Objects.requireNonNull(eligibilityPort, "eligibilityPort");
    }

    public Preparation prepare(RealtimeEligibilityContext context) {
        RealtimeEligibilityResult result = eligibilityPort.assess(context);
        if (result.decision().status() != RealtimeEligibilityStatus.ELIGIBLE) {
            return new Preparation(result, Optional.empty());
        }
        var evidence = result.evidence();
        RealtimeEligibilityRevisionFence fence = new RealtimeEligibilityRevisionFence(
                evidence.candidateDirectoryRevision(), evidence.claimDirectoryRevision(),
                evidence.directoryResultHash(), evidence.directoryContractHash(), evidence.verifiedAt());
        RealtimeEligibilityVerificationToken token = new RealtimeEligibilityVerificationToken(
                context.query().taskId(), context.query().candidateUserId(), context.facts().taskVersion(),
                evidence.candidatePoolHash(), evidence.runtimeBindingHash(), fence, evidence.eligibilityHash(),
                evidence.verifiedAt(), evidence.expiresAt());
        return new Preparation(result, Optional.of(token));
    }

    public Verification verify(RealtimeEligibilityVerificationToken token,
                               RealtimeEligibilityVerificationFacts facts) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(facts, "facts");
        if (!facts.now().isBefore(token.expiresAt())) {
            return Verification.reprepare(RealtimeEligibilityFailureCode.EVIDENCE_EXPIRED);
        }
        if (!facts.candidatePoolHash().equals(token.candidatePoolHash())) {
            return Verification.reprepare(RealtimeEligibilityFailureCode.HASH_MISMATCH);
        }
        if (!facts.runtimeBindingHash().equals(token.runtimeBindingHash())) {
            return Verification.reprepare(RealtimeEligibilityFailureCode.BINDING_MISMATCH);
        }
        if (facts.taskVersion() != token.taskVersion()) {
            return Verification.reprepare(RealtimeEligibilityFailureCode.HASH_MISMATCH);
        }
        if (facts.activeClaimExists()) {
            return Verification.reprepare(RealtimeEligibilityFailureCode.ACTIVE_CLAIM_EXISTS);
        }
        return new Verification(true, false, null);
    }

    public record Preparation(RealtimeEligibilityResult result,
                              Optional<RealtimeEligibilityVerificationToken> verificationToken) {
        public Preparation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(verificationToken, "verificationToken");
        }
    }

    public record Verification(boolean valid, boolean reprepareRequired,
                               RealtimeEligibilityFailureCode failureCode) {
        public Verification {
            if (valid == reprepareRequired) throw new IllegalArgumentException("verification state is inconsistent");
            if (valid && failureCode != null) throw new IllegalArgumentException("valid verification cannot fail");
            if (!valid && failureCode == null) throw new IllegalArgumentException("invalid verification requires code");
        }

        static Verification reprepare(RealtimeEligibilityFailureCode code) {
            return new Verification(false, true, code);
        }
    }
}
