package cn.gov.enterprise.modules.workflow.domain.sandbox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable step-by-step Sandbox hash ledger; contains no production identity. */
public record SandboxAuditEvidence(Map<String, String> stepHashes) {
    public SandboxAuditEvidence {
        stepHashes = Map.copyOf(new LinkedHashMap<>(stepHashes));
        if (stepHashes.isEmpty()) throw new IllegalArgumentException("Sandbox audit must not be empty");
        stepHashes.forEach((step, hash) -> {
            if (step == null || step.isBlank() || hash == null || !hash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid Sandbox audit evidence");
            }
        });
    }
}
