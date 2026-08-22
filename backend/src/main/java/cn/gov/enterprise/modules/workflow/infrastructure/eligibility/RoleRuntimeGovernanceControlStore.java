package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Versioned, append-only runtime control reader. Missing or ambiguous controls fail closed. */
@Repository
public class RoleRuntimeGovernanceControlStore {
    private final JdbcTemplate jdbc;

    public RoleRuntimeGovernanceControlStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Control> latest(String type, String scopeKey, Instant at) {
        LocalDateTime effective = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
        var rows = jdbc.query("""
                SELECT decision,config_version,policy_payload_hash,evidence_hash,effective_from,effective_to
                  FROM workflow_role_runtime_governance_control
                 WHERE control_type=? AND scope_key=? AND deleted=0
                   AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)
                 ORDER BY config_version DESC LIMIT 1
                """, (rs, n) -> new Control(rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(),
                rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()),
                type, scopeKey, effective, effective);
        if (rows.size() != 1) return Optional.empty();
        return Optional.of(rows.get(0));
    }

    public record Control(String decision, long configVersion, String policyHash,
            String evidenceHash, Instant effectiveFrom, Instant effectiveTo) { }
}
