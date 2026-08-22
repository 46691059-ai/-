package cn.gov.enterprise.modules.workflow.domain.role;

import java.util.Comparator;
import java.util.List;

/** ROLE_RUNTIME_ACTIVATION_CANONICAL_V1 serialization and hashing. */
final class RoleRuntimeActivationCanonical {
    static final String VERSION = "ROLE_RUNTIME_ACTIVATION_CANONICAL_V1";

    private RoleRuntimeActivationCanonical() { }

    static String requestHash(RoleRuntimeActivationRequest request) {
        String canonical = "{\"bindingContractHash\":\"" + request.bindingContractHash()
                + "\",\"businessScope\":\"" + escape(request.businessScope())
                + "\",\"candidateContractHash\":\"" + request.candidateContractHash()
                + "\",\"directoryContractHash\":\"" + request.directoryContractHash()
                + "\",\"effectiveAt\":\"" + request.effectiveAt()
                + "\",\"resolverCode\":\"" + request.resolverCode().value()
                + "\",\"resolverContractHash\":\"" + request.contractHash().value()
                + "\",\"resolverVersion\":\"" + request.resolverVersion().value()
                + "\",\"schema\":\"" + VERSION + "\"}";
        return RoleDirectoryCanonical.sha256(canonical);
    }

    static String snapshotHash(
            RoleRuntimeActivationRequest request,
            String activationHash,
            List<RoleRuntimeActivationDecision> decisions) {
        String decisionEvidence = decisions.stream()
                .sorted(Comparator.comparing(item -> item.approverRole().name()))
                .map(item -> item.approverRole().name() + ':' + item.decision().name()
                        + ':' + escape(item.approver())
                        + ':' + escape(item.reason())
                        + ':' + item.timestamp()
                        + ':' + item.evidenceHash())
                .reduce((left, right) -> left + '|' + right).orElse("");
        String canonical = "{\"activationHash\":\"" + activationHash
                + "\",\"decisionEvidence\":\"" + decisionEvidence
                + "\",\"requestHash\":\"" + request.canonicalHash()
                + "\",\"schema\":\"" + VERSION + "\"}";
        return RoleDirectoryCanonical.sha256(canonical);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
