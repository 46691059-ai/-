package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowAuditedEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityEventEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRealtimeEligibilityCapabilityEvidenceMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRealtimeEligibilityEventMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRealtimeEligibilityValidatorEvidenceMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in real MySQL mapper regression; the RC2-S7 validation script supplies the URL. */
@SpringBootTest
@EnabledIfSystemProperty(named = "rc2.v2623.mysql.url", matches = ".+")
class V2623RealtimeEligibilityRemarkMysqlMappingTest {
    private static final long EVIDENCE_ID = 9_900_001L;
    private static final LocalDateTime CHECKED_AT =
            LocalDateTime.of(2026, 8, 24, 1, 0);
    private static final DateTimeFormatter MYSQL_CANONICAL_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getProperty("rc2.v2623.mysql.url"));
        registry.add("spring.datasource.username",
                () -> System.getProperty("rc2.v2623.mysql.user", "root"));
        registry.add("spring.datasource.password",
                () -> System.getProperty("rc2.v2623.mysql.password", ""));
        registry.add("spring.datasource.hikari.data-source-properties.useSSL", () -> "false");
        registry.add("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval",
                () -> "true");
        registry.add("spring.datasource.hikari.data-source-properties.serverTimezone",
                () -> "Asia/Shanghai");
        registry.add("app.security.jwt.secret",
                () -> "RC2_S7_ISOLATED_MYSQL_TEST_SECRET_20260824");
        registry.add("app.database.mapping-check.enabled", () -> "true");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowRoleRealtimeEligibilityValidatorEvidenceMapper validators;
    @Autowired private WorkflowRoleRealtimeEligibilityCapabilityEvidenceMapper capabilities;
    @Autowired private WorkflowRoleRealtimeEligibilityEventMapper events;

    @Test
    @Transactional
    void realMappersPersistNullAndNonNullRemarkAndReadBackWithoutDrift() {
        insertIsolatedParentEvidence();

        var validatorNull = validator(9_901_001L, 1, "VALIDATOR_ONE", null);
        var validatorRemark = validator(9_901_002L, 2, "VALIDATOR_TWO", "RC2_TEST_REMARK");
        assertThat(validators.insert(validatorNull)).isEqualTo(1);
        assertThat(validators.insert(validatorRemark)).isEqualTo(1);

        var capabilityNull = capability(9_902_001L, "ROLE_MEMBERSHIP", null);
        var capabilityRemark = capability(9_902_002L, "USER_STATUS", "RC2_TEST_REMARK");
        assertThat(capabilities.insert(capabilityNull)).isEqualTo(1);
        assertThat(capabilities.insert(capabilityRemark)).isEqualTo(1);

        var eventNull = event(9_903_001L, 1, "PREPARED", null, null);
        assertThat(events.insert(eventNull)).isEqualTo(1);
        var eventRemark = event(9_903_002L, 2, "VERIFIED",
                eventNull.getEventHash(), "RC2_TEST_REMARK");
        assertThat(events.insert(eventRemark)).isEqualTo(1);

        assertThat(validators.selectById(validatorNull.getId()).getRemark()).isNull();
        assertThat(capabilities.selectById(capabilityNull.getId()).getRemark()).isNull();
        assertThat(events.selectById(eventNull.getId()).getRemark()).isNull();
        assertThat(validators.selectById(validatorRemark.getId()).getRemark())
                .isEqualTo("RC2_TEST_REMARK");
        assertThat(capabilities.selectById(capabilityRemark.getId()).getRemark())
                .isEqualTo("RC2_TEST_REMARK");
        assertThat(events.selectById(eventRemark.getId()).getRemark())
                .isEqualTo("RC2_TEST_REMARK");

        Integer exactColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema=DATABASE()
                   AND table_name IN (
                     'workflow_role_realtime_eligibility_capability_evidence',
                     'workflow_role_realtime_eligibility_event',
                     'workflow_role_realtime_eligibility_validator_evidence')
                   AND column_name='remark' AND column_type='varchar(500)'
                   AND is_nullable='YES' AND column_default IS NULL
                   AND character_set_name='utf8mb4'
                   AND collation_name='utf8mb4_0900_ai_ci'
                """, Integer.class);
        assertThat(exactColumns).isEqualTo(3);
    }

    private void insertIsolatedParentEvidence() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            String hash = "a".repeat(64);
            jdbc.update("""
                INSERT INTO workflow_role_realtime_eligibility_evidence (
                  id,eligibility_evidence_id,eligibility_request_id,claim_request_id,
                  claim_idempotency_key,attempt_no,correlation_id,instance_id,
                  definition_version_id,node_id,node_execution_id,task_id,candidate_pool_id,
                  candidate_member_id,candidate_user_id,binding_set_id,resolver_binding_id,
                  node_resolver_binding_id,role_code,organization_id,candidate_pool_hash,
                  runtime_binding_hash,eligibility_hash,candidate_directory_revision,
                  claim_directory_revision,directory_result_hash,directory_contract_hash,
                  directory_complete,directory_effective_at,directory_checked_at,decision,
                  terminal_validator_order,validator_count,capability_count,
                  validator_evidence_root_hash,capability_evidence_root_hash,persistence_hash,
                  eligibility_canonical_version,persistence_canonical_version,policy_version,
                  evidence_source,claim_at,verified_at,expires_at,created_by,updated_by,
                  deleted,delete_token,remark,version)
                VALUES (?,?,?,?,?,1,?,1,1,1,1,1,1,1,701,1,1,1,
                  'ROLE_APPROVER','8',?,?,?,?,?,?,?,1,
                  '2026-08-24 00:00:00.000','2026-08-24 00:00:00.000','INELIGIBLE',
                  1,1,0,?,?,?,'ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1',
                  'ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1','POLICY_V1',
                  'CONTRACT_TEST','2026-08-24 02:00:00.000','2026-08-24 01:00:00.000',
                  '2026-08-25 00:00:00.000','RC2_S7','RC2_S7',0,0,NULL,0)
                """, EVIDENCE_ID, "EVIDENCE-S7", "REQUEST-S7", "CLAIM-REQUEST-S7",
                    "IDEMPOTENCY-S7", "CORRELATION-S7", hash, hash, hash,
                    "REVISION-1", "REVISION-1", hash, hash, hash, hash, hash);
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity validator(
            long id, int order, String code, String remark) {
        String reason = "RC2_S7_MAPPING";
        String canonical = "ROLE_REALTIME_VALIDATOR_EVIDENCE_V1"
                + sized(Integer.toString(order)) + sized(code) + sized("PASS") + sized(reason)
                + CHECKED_AT.format(MYSQL_CANONICAL_TIME);
        var entity = new WorkflowRoleRealtimeEligibilityValidatorEvidenceEntity();
        audited(entity, id, remark);
        entity.setEvidenceId(EVIDENCE_ID); entity.setValidatorCode(code);
        entity.setValidatorOrder(order); entity.setStatus("PASS"); entity.setReasonCode(reason);
        entity.setEvidenceHash(sha256(canonical)); entity.setCheckedAt(CHECKED_AT);
        entity.setCanonicalVersion("ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1");
        return entity;
    }

    private WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity capability(
            long id, String code, String remark) {
        String validator = "CAPABILITY_VALIDATOR";
        String canonical = "ROLE_REALTIME_CAPABILITY_EVIDENCE_V1"
                + sized(code) + sized(validator) + sized("PASS") + sized("PASS")
                + sized("PROVIDER_V1") + sized("POLICY_V1")
                + CHECKED_AT.format(MYSQL_CANONICAL_TIME) + "null";
        var entity = new WorkflowRoleRealtimeEligibilityCapabilityEvidenceEntity();
        audited(entity, id, remark);
        entity.setEvidenceId(EVIDENCE_ID); entity.setCapabilityCode(code);
        entity.setValidatorCode(validator); entity.setStatus("PASS"); entity.setDecision("PASS");
        entity.setProviderVersion("PROVIDER_V1"); entity.setPolicyVersion("POLICY_V1");
        entity.setEvidenceHash(sha256(canonical)); entity.setCheckedAt(CHECKED_AT);
        entity.setCanonicalVersion("ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1");
        return entity;
    }

    private WorkflowRoleRealtimeEligibilityEventEntity event(
            long id, long sequence, String type, String previousHash, String remark) {
        String reason = "RC2_S7_MAPPING";
        String operator = "RC2_S7";
        String idempotency = "RC2-S7-" + sequence;
        String canonical = "ROLE_REALTIME_ELIGIBILITY_EVENT_V1"
                + sized(Long.toString(EVIDENCE_ID)) + sized(Long.toString(sequence))
                + sized(type) + "null" + sized(reason)
                + (previousHash == null ? "null" : previousHash)
                + CHECKED_AT.plusMinutes(sequence).format(MYSQL_CANONICAL_TIME)
                + sized(operator) + sized(idempotency);
        var entity = new WorkflowRoleRealtimeEligibilityEventEntity();
        audited(entity, id, remark);
        entity.setEvidenceId(EVIDENCE_ID); entity.setSequenceNo(sequence);
        entity.setEventType(type); entity.setReasonCode(reason);
        entity.setPreviousEventHash(previousHash); entity.setEventHash(sha256(canonical));
        entity.setOccurredAt(CHECKED_AT.plusMinutes(sequence)); entity.setOperatorId(operator);
        entity.setIdempotencyKey(idempotency);
        entity.setCanonicalVersion("ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1");
        return entity;
    }

    private void audited(WorkflowAuditedEntity entity, long id, String remark) {
        entity.setId(id); entity.setCreatedBy("RC2_S7"); entity.setUpdatedBy("RC2_S7");
        entity.setDeleted(0); entity.setDeleteToken(0L); entity.setRemark(remark);
        entity.setVersion(0);
    }

    private static String sized(String value) {
        return value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
