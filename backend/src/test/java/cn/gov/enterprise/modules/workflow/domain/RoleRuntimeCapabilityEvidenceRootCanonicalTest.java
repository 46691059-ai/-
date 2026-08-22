package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeCapabilityEvidenceRootCanonical;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionEvidenceRecord;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionValidatorContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RoleRuntimeCapabilityEvidenceRootCanonicalTest {
    private static final String EXPECTED = "9f73f6861c4969751365f82d540715ec272f336b193d0f95a41de903b9f1e5cb";
    private static final String H = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-17T09:00:00Z");

    @Test void fixedVectorMustMatchDatabaseCanonicalAndIgnoreInputOrder() throws Exception {
        var normal = capabilities("READY", "FAKE_V1", "POLICY_V1");
        var reversed = new ArrayList<>(normal);
        Collections.reverse(reversed);
        assertThat(RoleRuntimeCapabilityEvidenceRootCanonical.compute(normal)).isEqualTo(EXPECTED);
        assertThat(RoleRuntimeCapabilityEvidenceRootCanonical.compute(reversed)).isEqualTo(EXPECTED);
        String sql = Files.readString(Path.of("..", "database", "migration", "mysql",
                "V2.6.15__create_role_runtime_execution_admission_persistence.sql"));
        assertThat(sql).contains("9:canonical50:" + RoleRuntimeCapabilityEvidenceRootCanonical.VERSION,
                "13:validatorCode", "14:capabilityCode", "16:capabilityStatus",
                "12:evidenceHash", "15:providerVersion", "13:policyVersion");
    }

    @Test void everyFrozenInputMustAffectRoot() {
        String base = RoleRuntimeCapabilityEvidenceRootCanonical.compute(
                capabilities("READY", "FAKE_V1", "POLICY_V1"));
        assertThat(List.of(
                RoleRuntimeCapabilityEvidenceRootCanonical.compute(capabilities("NOT_READY", "FAKE_V1", "POLICY_V1")),
                RoleRuntimeCapabilityEvidenceRootCanonical.compute(capabilities("READY", "FAKE_V2", "POLICY_V1")),
                RoleRuntimeCapabilityEvidenceRootCanonical.compute(capabilities("READY", "FAKE_V1", "POLICY_V2")),
                RoleRuntimeCapabilityEvidenceRootCanonical.compute(changedEvidenceHash())))
                .allMatch(value -> !value.equals(base));
    }

    private static List<RoleRuntimeExecutionAdmissionEvidenceRecord> capabilities(
            String status, String provider, String policy) {
        return IntStream.rangeClosed(21, 28).mapToObj(sequence -> evidence(sequence,
                RoleRuntimeExecutionAdmissionValidatorContract.REQUIRED_CAPABILITIES.get(sequence),
                status, provider, policy, String.format("%064x", sequence))).toList();
    }

    private static List<RoleRuntimeExecutionAdmissionEvidenceRecord> changedEvidenceHash() {
        var values = new ArrayList<>(capabilities("READY", "FAKE_V1", "POLICY_V1"));
        var original = values.getFirst();
        values.set(0, evidence(original.sequenceNo(), original.capabilityCode(), "READY", "FAKE_V1",
                "POLICY_V1", "f".repeat(64)));
        return values;
    }

    private static RoleRuntimeExecutionAdmissionEvidenceRecord evidence(int sequence, String capability,
            String status, String provider, String policy, String evidenceHash) {
        return new RoleRuntimeExecutionAdmissionEvidenceRecord((long) sequence, 1L, "ADM-1", sequence,
                RoleRuntimeExecutionAdmissionValidatorContract.validatorCode(sequence), "CAPABILITY", "PASS", null,
                capability, status, provider, policy, "READY", "ENTERPRISE", "ENT-1", 1L, 2L, 3L,
                NOW, H, evidenceHash, "EVIDENCE_V1");
    }
}
