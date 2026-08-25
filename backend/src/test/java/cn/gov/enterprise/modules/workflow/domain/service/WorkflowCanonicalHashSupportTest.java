package cn.gov.enterprise.modules.workflow.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WorkflowCanonicalHashSupportTest {
    @Test
    void utf8Sha256MustMatchAnIndependentJdkDigest() throws Exception {
        String canonical = "2:审批|-1:|0:|";
        String independent = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        assertThat(WorkflowCanonicalHashSupport.sha256(canonical)).isEqualTo(independent);
        assertThat(WorkflowCanonicalHashSupport.canonical("审批", null, ""))
                .isEqualTo(canonical);
    }

    @Test
    void hashFormatMustBeLowercaseHex() {
        assertThatThrownBy(() -> WorkflowCanonicalHashSupport.requireSha256(
                "A".repeat(64), "hash")).hasMessageContaining("lowercase SHA-256");
        assertThatThrownBy(() -> WorkflowCanonicalHashSupport.requireSha256(
                "a".repeat(63), "hash")).hasMessageContaining("lowercase SHA-256");
        assertThatThrownBy(() -> WorkflowCanonicalHashSupport.requireSha256(
                null, "hash")).hasMessageContaining("lowercase SHA-256");
    }
}
