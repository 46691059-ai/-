package cn.gov.enterprise.modules.workflow.domain.role.admission.persistence;

import java.util.List;
import java.util.Map;

/** Frozen ROLE admission validator contract shared by persistence validation and tests. */
public final class RoleRuntimeExecutionAdmissionValidatorContract {
    public static final String VERSION = "ROLE_RUNTIME_EXECUTION_ADMISSION_VALIDATOR_CONTRACT_V1";
    public static final List<String> VALIDATOR_CODES = List.of(
            "PERSISTED_CANDIDATE_EXISTS", "CANDIDATE_SNAPSHOT_STATUS",
            "PROMOTION_EVIDENCE_COMPLETE", "ACTIVATION_EVIDENCE_COMPLETE",
            "ACTIVATION_NOT_REVOKED", "PROMOTION_NOT_REVOKED", "RESOLVER_CODE",
            "RESOLVER_VERSION", "RESOLVER_CONTRACT_HASH", "ACTIVATION_HASH",
            "PROMOTION_HASH", "BINDING_HASH", "CANDIDATE_HASH",
            "DIRECTORY_REVISION_AND_RESULT_HASH", "EFFECTIVE_AT", "BUSINESS_SCOPE",
            "DEFINITION_VERSION", "NODE_BINDING", "RESOLVER_DESCRIPTOR",
            "RESOLVER_ADMISSION_STATUS", "DIRECTORY_READY", "REALTIME_ELIGIBILITY_READY",
            "DATA_SCOPE_READY", "SOD_READY", "AUDIT_READY", "FEATURE_FLAG_READY",
            "KILL_SWITCH_READY", "CANARY_SCOPE_READY");

    public static final Map<Integer, String> REQUIRED_CAPABILITIES = Map.of(
            21, "DIRECTORY", 22, "REALTIME_ELIGIBILITY", 23, "DATA_SCOPE", 24, "SOD",
            25, "AUDIT", 26, "FEATURE_FLAG", 27, "KILL_SWITCH", 28, "CANARY_SCOPE");

    private RoleRuntimeExecutionAdmissionValidatorContract() { }

    public static String validatorCode(int sequenceNo) {
        if (sequenceNo < 1 || sequenceNo > VALIDATOR_CODES.size()) {
            throw new IllegalArgumentException("validator sequence must be between 1 and 28");
        }
        return VALIDATOR_CODES.get(sequenceNo - 1);
    }
}
