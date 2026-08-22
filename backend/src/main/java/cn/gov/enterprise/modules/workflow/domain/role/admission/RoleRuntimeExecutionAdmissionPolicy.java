package cn.gov.enterprise.modules.workflow.domain.role.admission;

import java.time.Instant;
import java.util.Objects;

/** Pure-domain admission orchestration. */
public final class RoleRuntimeExecutionAdmissionPolicy {
    private final RoleRuntimeExecutionAdmissionValidator validator;

    public RoleRuntimeExecutionAdmissionPolicy(RoleRuntimeExecutionAdmissionValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public RoleRuntimeExecutionAdmissionDecision evaluate(
            RoleRuntimeExecutionAdmissionRequest request,
            RoleRuntimeExecutionAdmissionFacts facts,
            Instant validatedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(validatedAt, "validatedAt");
        RoleRuntimeExecutionAdmissionDecision decision =
                RoleRuntimeExecutionAdmissionDecision.created(request.requestId(), validatedAt)
                        .transition(RoleRuntimeExecutionAdmissionStatus.VALIDATING, validatedAt);
        RoleRuntimeExecutionAdmissionValidation validation =
                validator.validate(request, facts, validatedAt);
        String admissionHash = RoleRuntimeExecutionAdmissionHash.compute(request);
        RoleRuntimeExecutionAdmissionStatus evidenceStatus = validation.eligible()
                ? RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION
                : RoleRuntimeExecutionAdmissionStatus.BLOCKED;
        RoleRuntimeExecutionAdmissionEvidence evidence = new RoleRuntimeExecutionAdmissionEvidence(
                request.requestId(), admissionHash, request.activationHash(),
                request.promotionHash(), request.bindingHash(), request.candidateHash(),
                request.resolverContractHash(), request.directoryRevision(),
                request.directoryResultHash(), request.definitionVersionId(), request.nodeId(),
                validation.checks(), validation.capabilities(), evidenceStatus, validatedAt);
        if (!validation.eligible()) {
            return decision.blocked(validation.reason(), evidence, validatedAt);
        }
        decision = decision.transition(RoleRuntimeExecutionAdmissionStatus.ELIGIBLE, validatedAt);
        return decision.approved(evidence,
                ExecutableRuntimeBindingCandidate.from(request, admissionHash), validatedAt);
    }
}
