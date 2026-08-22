-- Sprint 2-3.7-WF5.18: append-only ROLE execution-admission persistence foundation.
-- This candidate does not enable ROLE runtime and never backfills legacy candidates.
USE enterprise_platform;

-- Phase 1: fail before permanent DDL. No history is repaired, inferred or deleted.
CREATE TEMPORARY TABLE tmp_role_admission_v2615_guard (
    phase_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    violation_count BIGINT NOT NULL,
    PRIMARY KEY (phase_code),
    CONSTRAINT chk_role_admission_v2615_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_admission_v2615_guard (phase_code, violation_count)
SELECT 'BASELINE_OBJECTS',
       ABS(8 - (SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE() AND table_name IN (
                   'role_runtime_activation_request','workflow_role_binding_promotion',
                   'workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event',
                   'workflow_definition','workflow_version','workflow_node','workflow_version_release')))
     + (SELECT COUNT(*) FROM information_schema.tables
          WHERE table_schema = DATABASE() AND table_name IN (
            'workflow_role_runtime_execution_admission',
            'workflow_role_runtime_execution_admission_evidence',
            'workflow_role_runtime_execution_admission_event',
            'workflow_role_runtime_execution_admission_slot'))
     + (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'workflow_role_binding_candidate_snapshot'
            AND column_name IN ('directory_result_hash','directory_fence_token_hash',
              'directory_fence_expires_at','enterprise_id','definition_release_id',
              'definition_id','definition_version_id','node_id','node_binding_hash','graph_hash'));

INSERT INTO tmp_role_admission_v2615_guard (phase_code, violation_count)
SELECT 'OWNER_KEYS',
       ABS(5 - (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND seq_in_index = 1 AND (
                   (table_name='workflow_role_binding_candidate_snapshot' AND index_name='uk_role_binding_snapshot_owner') OR
                   (table_name='workflow_role_binding_promotion' AND index_name='uk_role_binding_promotion_owner') OR
                   (table_name='workflow_version' AND index_name='uk_workflow_version_owner') OR
                   (table_name='workflow_node' AND index_name='uk_workflow_node_owner') OR
                   (table_name='workflow_version_release' AND index_name='PRIMARY'))));

INSERT INTO tmp_role_admission_v2615_guard (phase_code, violation_count)
SELECT 'SOURCE_INTEGRITY',
       (SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot c
          LEFT JOIN workflow_role_binding_promotion p
            ON p.id=c.promotion_row_id AND p.promotion_id=c.promotion_id
           AND p.activation_id=c.activation_id AND p.delete_token=c.delete_token
         WHERE p.id IS NULL OR c.activation_hash<>p.activation_hash
            OR c.promotion_hash<>p.promotion_hash OR c.binding_hash<>p.binding_hash
            OR c.candidate_hash<>p.candidate_hash OR c.resolver_code<>p.resolver_code
            OR c.resolver_version<>p.resolver_version
            OR c.resolver_contract_hash<>p.resolver_contract_hash
            OR c.directory_revision<>p.directory_revision)
     + (SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot
         WHERE resolver_code IS NULL OR resolver_version IS NULL
            OR NOT REGEXP_LIKE(resolver_code,'^[A-Z0-9_]+$','c')
            OR NOT REGEXP_LIKE(resolver_version,'^[A-Z0-9_]+$','c')
            OR resolver_contract_hash NOT REGEXP '^[0-9a-f]{64}$'
            OR activation_hash NOT REGEXP '^[0-9a-f]{64}$'
            OR promotion_hash NOT REGEXP '^[0-9a-f]{64}$'
            OR binding_hash NOT REGEXP '^[0-9a-f]{64}$'
            OR candidate_hash NOT REGEXP '^[0-9a-f]{64}$');

INSERT INTO tmp_role_admission_v2615_guard (phase_code, violation_count)
SELECT 'NO_PARTIAL_INSTALL',
       (SELECT COUNT(*) FROM information_schema.triggers
         WHERE trigger_schema=DATABASE() AND trigger_name LIKE 'trg_role_admission_%')
     + (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND index_name IN (
           'uk_role_binding_snapshot_slot_owner',
           'uk_role_admission_owner','uk_role_admission_evidence_owner',
           'uk_role_admission_id','uk_role_admission_request',
           'uk_role_admission_persistence_hash','uk_role_admission_idempotency',
           'uk_role_admission_slot_source','uk_role_admission_slot_active',
           'uk_role_admission_evidence_sequence','uk_role_admission_evidence_code',
           'uk_role_admission_event_sequence','uk_role_admission_event_hash',
           'uk_role_admission_event_idempotency'))
     + (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema=DATABASE() AND constraint_name LIKE '%role_admission%');

DROP TEMPORARY TABLE tmp_role_admission_v2615_guard;

-- Phase 2: nullable, future-only source evidence. Legacy rows intentionally remain NULL.
ALTER TABLE workflow_role_binding_candidate_snapshot
    ADD COLUMN directory_result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN directory_fence_token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN directory_fence_expires_at DATETIME(3) NULL,
    ADD COLUMN enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN definition_release_id BIGINT NULL,
    ADD COLUMN definition_id BIGINT NULL,
    ADD COLUMN definition_version_id BIGINT NULL,
    ADD COLUMN node_id BIGINT NULL,
    ADD COLUMN node_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN graph_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT chk_role_binding_snapshot_admission_evidence CHECK (
      (directory_result_hash IS NULL AND directory_fence_token_hash IS NULL
       AND directory_fence_expires_at IS NULL AND enterprise_id IS NULL
       AND definition_release_id IS NULL AND definition_id IS NULL
       AND definition_version_id IS NULL AND node_id IS NULL
       AND node_binding_hash IS NULL AND graph_hash IS NULL)
      OR
      (directory_result_hash REGEXP '^[0-9a-f]{64}$'
       AND directory_fence_token_hash REGEXP '^[0-9a-f]{64}$'
       AND directory_fence_expires_at IS NOT NULL AND CHAR_LENGTH(TRIM(enterprise_id)) > 0
       AND definition_release_id IS NOT NULL AND definition_id IS NOT NULL
       AND definition_version_id IS NOT NULL AND node_id IS NOT NULL
       AND node_binding_hash REGEXP '^[0-9a-f]{64}$' AND graph_hash REGEXP '^[0-9a-f]{64}$')),
    ADD KEY idx_role_binding_snapshot_definition (definition_id, definition_version_id, node_id),
    ADD KEY idx_role_binding_snapshot_release (definition_release_id),
    ADD UNIQUE KEY uk_role_binding_snapshot_slot_owner (id,snapshot_id,delete_token);

-- Phase 3: immutable stable decision.
CREATE TABLE workflow_role_runtime_execution_admission (
    id BIGINT NOT NULL,
    admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_snapshot_row_id BIGINT NOT NULL,
    snapshot_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_delete_token BIGINT NOT NULL DEFAULT 0,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_admission_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capability_evidence_root_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    persistence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision BIGINT NOT NULL,
    directory_result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_fence_token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_fence_expires_at DATETIME(3) NOT NULL,
    directory_verified_at DATETIME(3) NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_scope VARCHAR(200) NOT NULL,
    definition_release_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    node_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    graph_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    admission_expires_at DATETIME(3) NOT NULL,
    executed_check_count INT NOT NULL,
    last_check_sequence INT NOT NULL,
    feature_flag_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canary_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kill_switch_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    decided_by VARCHAR(100) NOT NULL,
    decided_at DATETIME(3) NOT NULL,
    created_by VARCHAR(64) NULL, created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL, updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0, delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_admission_owner (id,admission_id,candidate_snapshot_row_id,delete_token),
    UNIQUE KEY uk_role_admission_evidence_owner (id,admission_id,delete_token),
    UNIQUE KEY uk_role_admission_id (admission_id,delete_token),
    UNIQUE KEY uk_role_admission_request (request_id,delete_token),
    UNIQUE KEY uk_role_admission_persistence_hash (persistence_hash,delete_token),
    UNIQUE KEY uk_role_admission_idempotency (candidate_snapshot_row_id,idempotency_key,delete_token),
    KEY idx_role_admission_candidate_decision (candidate_snapshot_row_id,decision),
    KEY idx_role_admission_scope (enterprise_id,definition_id,node_id,decision),
    KEY idx_role_admission_resolver (resolver_code,resolver_version),
    KEY idx_role_admission_decided (decided_at),
    CONSTRAINT chk_role_admission_identity CHECK (
      REGEXP_LIKE(resolver_code,'^[A-Z0-9_]+$','c') AND REGEXP_LIKE(resolver_version,'^[A-Z0-9_]+$','c')),
    CONSTRAINT chk_role_admission_hashes CHECK (
      activation_hash REGEXP '^[0-9a-f]{64}$' AND promotion_hash REGEXP '^[0-9a-f]{64}$'
      AND binding_hash REGEXP '^[0-9a-f]{64}$' AND candidate_hash REGEXP '^[0-9a-f]{64}$'
      AND execution_admission_hash REGEXP '^[0-9a-f]{64}$'
      AND capability_evidence_root_hash REGEXP '^[0-9a-f]{64}$'
      AND persistence_hash REGEXP '^[0-9a-f]{64}$' AND resolver_contract_hash REGEXP '^[0-9a-f]{64}$'
      AND directory_result_hash REGEXP '^[0-9a-f]{64}$' AND directory_fence_token_hash REGEXP '^[0-9a-f]{64}$'
      AND node_binding_hash REGEXP '^[0-9a-f]{64}$' AND graph_hash REGEXP '^[0-9a-f]{64}$'
      AND feature_flag_evidence_hash REGEXP '^[0-9a-f]{64}$'
      AND canary_evidence_hash REGEXP '^[0-9a-f]{64}$' AND kill_switch_evidence_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_admission_decision CHECK (decision IN ('ELIGIBLE','APPROVED_FOR_EXECUTION','BLOCKED','REJECTED')),
    CONSTRAINT chk_role_admission_evidence_count CHECK (executed_check_count BETWEEN 1 AND 28 AND last_check_sequence=executed_check_count),
    CONSTRAINT chk_role_admission_time CHECK (directory_fence_expires_at>directory_verified_at AND admission_expires_at>effective_at),
    CONSTRAINT chk_role_admission_append_only CHECK (deleted=0 AND delete_token=0 AND source_delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 6: mutable CAS pointer only; it is not an audit fact.
CREATE TABLE workflow_role_runtime_execution_admission_slot (
    candidate_snapshot_row_id BIGINT NOT NULL,
    snapshot_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_delete_token BIGINT NOT NULL DEFAULT 0,
    active_admission_row_id BIGINT NULL,
    active_admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    slot_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VACANT',
    version INT NOT NULL DEFAULT 0, updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(candidate_snapshot_row_id),
    UNIQUE KEY uk_role_admission_slot_source (candidate_snapshot_row_id,snapshot_id,source_delete_token),
    UNIQUE KEY uk_role_admission_slot_active (active_admission_row_id),
    CONSTRAINT chk_role_admission_slot_pointer CHECK (
      (slot_status='VACANT' AND active_admission_row_id IS NULL AND active_admission_id IS NULL)
      OR (slot_status='OCCUPIED' AND active_admission_row_id IS NOT NULL AND active_admission_id IS NOT NULL)),
    CONSTRAINT chk_role_admission_slot_version CHECK (version>=0 AND source_delete_token=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Phase 7: append-only validator and capability evidence.
CREATE TABLE workflow_role_runtime_execution_admission_evidence (
    id BIGINT NOT NULL, admission_row_id BIGINT NOT NULL,
    admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence_no INT NOT NULL, validator_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    block_reason VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    capability_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    capability_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    provider_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_value_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    scope_enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    scope_definition_id BIGINT NULL, scope_definition_version_id BIGINT NULL, scope_node_id BIGINT NULL,
    checked_at DATETIME(3) NOT NULL,
    subject_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL, created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL, updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0, delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    UNIQUE KEY uk_role_admission_evidence_sequence (admission_row_id,sequence_no,delete_token),
    UNIQUE KEY uk_role_admission_evidence_code (admission_row_id,validator_code,delete_token),
    KEY idx_role_admission_evidence_check (validator_code,result),
    KEY idx_role_admission_evidence_capability (capability_code,capability_status),
    KEY idx_role_admission_evidence_checked (checked_at),
    CONSTRAINT chk_role_admission_evidence_sequence CHECK (sequence_no BETWEEN 1 AND 28),
    CONSTRAINT chk_role_admission_evidence_result CHECK (
      (result='PASS' AND block_reason IS NULL) OR (result='FAIL' AND block_reason IS NOT NULL)),
    CONSTRAINT chk_role_admission_evidence_capability CHECK (
      (capability_code IS NULL AND capability_status IS NULL)
      OR (capability_code IS NOT NULL AND capability_status IN ('READY','NOT_READY','DEGRADED','BLOCKED'))),
    CONSTRAINT chk_role_admission_evidence_hash CHECK (subject_hash REGEXP '^[0-9a-f]{64}$' AND evidence_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_admission_evidence_append CHECK (deleted=0 AND delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 8: append-only lifecycle hash chain.
CREATE TABLE workflow_role_runtime_execution_admission_event (
    id BIGINT NOT NULL, admission_row_id BIGINT NOT NULL,
    admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_snapshot_row_id BIGINT NOT NULL, sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_evidence_root_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL, operator_id VARCHAR(100) NOT NULL,
    operator_role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL, created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL, updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0, delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    UNIQUE KEY uk_role_admission_event_sequence (admission_row_id,sequence_no,delete_token),
    UNIQUE KEY uk_role_admission_event_hash (event_hash,delete_token),
    UNIQUE KEY uk_role_admission_event_idempotency (admission_row_id,idempotency_key,delete_token),
    CONSTRAINT chk_role_admission_event_sequence CHECK (sequence_no>0),
    CONSTRAINT chk_role_admission_event_type CHECK (event_type IN ('ADMISSION_CREATED','ELIGIBLE','APPROVED_FOR_EXECUTION','BLOCKED','REJECTED','REVOKED','EXPIRED')),
    CONSTRAINT chk_role_admission_event_status CHECK (
      to_status IN ('CREATED','ELIGIBLE','APPROVED_FOR_EXECUTION','BLOCKED','REJECTED','REVOKED','EXPIRED')
      AND (from_status IS NULL OR from_status IN ('CREATED','ELIGIBLE','APPROVED_FOR_EXECUTION','BLOCKED','REJECTED'))),
    CONSTRAINT chk_role_admission_event_hash CHECK (source_evidence_root_hash REGEXP '^[0-9a-f]{64}$' AND event_hash REGEXP '^[0-9a-f]{64}$' AND (previous_event_hash IS NULL OR previous_event_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT chk_role_admission_event_append CHECK (deleted=0 AND delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Phase 9: prove every parent key contract before adding any ownership FK.
CREATE TEMPORARY TABLE tmp_role_admission_v2615_parent_key_proof (
    contract_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    violation_count BIGINT NOT NULL,
    PRIMARY KEY (contract_code),
    CONSTRAINT chk_role_admission_v2615_parent_key_proof CHECK (violation_count=0)
);

INSERT INTO tmp_role_admission_v2615_parent_key_proof VALUES
('CANDIDATE_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_role_binding_candidate_snapshot'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id,snapshot_id,promotion_id,activation_id,delete_token') p))),
('CANDIDATE_SLOT_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_role_binding_candidate_snapshot'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id,snapshot_id,delete_token') p))),
('WORKFLOW_VERSION_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_version'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='definition_id,id') p))),
('WORKFLOW_NODE_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_node'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='version_id,id') p))),
('WORKFLOW_RELEASE_ID', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_version_release'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id') p))),
('ADMISSION_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_role_runtime_execution_admission'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id,admission_id,candidate_snapshot_row_id,delete_token') p))),
('ADMISSION_EVIDENCE_OWNER', ABS(1-(SELECT COUNT(*) FROM (
  SELECT index_name FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='workflow_role_runtime_execution_admission'
     AND non_unique=0 GROUP BY index_name
  HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='id,admission_id,delete_token') p)));

DROP TEMPORARY TABLE tmp_role_admission_v2615_parent_key_proof;

ALTER TABLE workflow_role_binding_candidate_snapshot
    ADD CONSTRAINT fk_role_binding_snapshot_definition_version
      FOREIGN KEY (definition_id,definition_version_id)
      REFERENCES workflow_version(definition_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_binding_snapshot_node
      FOREIGN KEY (definition_version_id,node_id)
      REFERENCES workflow_node(version_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_binding_snapshot_release
      FOREIGN KEY (definition_release_id)
      REFERENCES workflow_version_release(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_runtime_execution_admission
    ADD CONSTRAINT fk_role_admission_candidate FOREIGN KEY
      (candidate_snapshot_row_id,snapshot_id,promotion_id,activation_id,source_delete_token)
      REFERENCES workflow_role_binding_candidate_snapshot(id,snapshot_id,promotion_id,activation_id,delete_token)
      ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_admission_definition_version FOREIGN KEY (definition_id,definition_version_id)
      REFERENCES workflow_version(definition_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_admission_node FOREIGN KEY (definition_version_id,node_id)
      REFERENCES workflow_node(version_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_admission_release FOREIGN KEY (definition_release_id)
      REFERENCES workflow_version_release(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_runtime_execution_admission_slot
    ADD CONSTRAINT fk_role_admission_slot_candidate FOREIGN KEY
      (candidate_snapshot_row_id,snapshot_id,source_delete_token)
      REFERENCES workflow_role_binding_candidate_snapshot(id,snapshot_id,delete_token)
      ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_admission_slot_active FOREIGN KEY
      (active_admission_row_id,active_admission_id,candidate_snapshot_row_id,source_delete_token)
      REFERENCES workflow_role_runtime_execution_admission(id,admission_id,candidate_snapshot_row_id,delete_token)
      ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_runtime_execution_admission_evidence
    ADD CONSTRAINT fk_role_admission_evidence_owner FOREIGN KEY (admission_row_id,admission_id,delete_token)
      REFERENCES workflow_role_runtime_execution_admission(id,admission_id,delete_token)
      ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_runtime_execution_admission_event
    ADD CONSTRAINT fk_role_admission_event_owner FOREIGN KEY
      (admission_row_id,admission_id,candidate_snapshot_row_id,delete_token)
      REFERENCES workflow_role_runtime_execution_admission(id,admission_id,candidate_snapshot_row_id,delete_token)
      ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Phase 10: table-local CHECK constraints were created with their tables; assert the FK layer is complete.
CREATE TEMPORARY TABLE tmp_role_admission_v2615_fk_assertion (
    assertion_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_admission_v2615_fk_assertion CHECK (violation_count=0)
);
INSERT INTO tmp_role_admission_v2615_fk_assertion VALUES ('OWNERSHIP_FKS',
  ABS(11-(SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE() AND constraint_name IN (
      'fk_role_binding_snapshot_definition_version','fk_role_binding_snapshot_node','fk_role_binding_snapshot_release',
      'fk_role_admission_candidate','fk_role_admission_definition_version','fk_role_admission_node','fk_role_admission_release',
      'fk_role_admission_slot_candidate','fk_role_admission_slot_active',
      'fk_role_admission_evidence_owner','fk_role_admission_event_owner'))));
DROP TEMPORARY TABLE tmp_role_admission_v2615_fk_assertion;

-- Phase 11: ownership and append-only guards. Stable SQLSTATE 45000 messages are audit contracts.
DELIMITER $$
CREATE TRIGGER trg_role_admission_insert_guard BEFORE INSERT ON workflow_role_runtime_execution_admission
FOR EACH ROW BEGIN
  DECLARE matches_count INT DEFAULT 0;
  SELECT COUNT(*) INTO matches_count
    FROM workflow_role_binding_candidate_snapshot c
    JOIN workflow_role_binding_snapshot_event e ON e.snapshot_row_id=c.id AND e.snapshot_id=c.snapshot_id
   WHERE c.id=NEW.candidate_snapshot_row_id AND c.snapshot_id=NEW.snapshot_id
     AND c.promotion_id=NEW.promotion_id AND c.activation_id=NEW.activation_id AND c.delete_token=NEW.source_delete_token
     AND c.activation_hash=NEW.activation_hash AND c.promotion_hash=NEW.promotion_hash
     AND c.binding_hash=NEW.binding_hash AND c.candidate_hash=NEW.candidate_hash
     AND c.resolver_code=NEW.resolver_code AND c.resolver_version=NEW.resolver_version
     AND c.resolver_contract_hash=NEW.resolver_contract_hash
     AND c.directory_revision=NEW.directory_revision AND c.directory_result_hash=NEW.directory_result_hash
     AND c.directory_fence_token_hash=NEW.directory_fence_token_hash
     AND c.directory_fence_expires_at=NEW.directory_fence_expires_at
     AND c.enterprise_id=NEW.enterprise_id AND c.business_scope=NEW.business_scope
     AND c.definition_release_id=NEW.definition_release_id AND c.definition_id=NEW.definition_id
     AND c.definition_version_id=NEW.definition_version_id AND c.node_id=NEW.node_id
     AND c.node_binding_hash=NEW.node_binding_hash AND c.graph_hash=NEW.graph_hash
     AND e.sequence_no=(SELECT MAX(e2.sequence_no) FROM workflow_role_binding_snapshot_event e2 WHERE e2.snapshot_row_id=c.id)
     AND e.to_status='ACTIVE';
  IF matches_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_FROZEN_EVIDENCE_MISMATCH'; END IF;
  SELECT COUNT(*) INTO matches_count FROM workflow_version_release r
   WHERE r.id=NEW.definition_release_id AND r.definition_id=NEW.definition_id
     AND r.published_version_id=NEW.definition_version_id AND r.content_hash=NEW.graph_hash;
  IF matches_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_RELEASE_MISMATCH'; END IF;
END$$
CREATE TRIGGER trg_role_admission_no_update BEFORE UPDATE ON workflow_role_runtime_execution_admission
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_no_delete BEFORE DELETE ON workflow_role_runtime_execution_admission
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_evidence_no_update BEFORE UPDATE ON workflow_role_runtime_execution_admission_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_EVIDENCE_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_evidence_no_delete BEFORE DELETE ON workflow_role_runtime_execution_admission_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_EVIDENCE_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_event_no_update BEFORE UPDATE ON workflow_role_runtime_execution_admission_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_EVENT_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_event_no_delete BEFORE DELETE ON workflow_role_runtime_execution_admission_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_EXECUTION_ADMISSION_EVENT_APPEND_ONLY'$$
CREATE TRIGGER trg_role_admission_event_insert_guard BEFORE INSERT ON workflow_role_runtime_execution_admission_event
FOR EACH ROW BEGIN
  DECLARE event_count INT DEFAULT 0; DECLARE latest_status VARCHAR(32); DECLARE latest_hash VARCHAR(64);
  SELECT COUNT(*) INTO event_count FROM workflow_role_runtime_execution_admission_event
   WHERE admission_row_id=NEW.admission_row_id;
  IF event_count>0 THEN
    SELECT to_status,event_hash INTO latest_status,latest_hash
      FROM workflow_role_runtime_execution_admission_event
     WHERE admission_row_id=NEW.admission_row_id ORDER BY sequence_no DESC LIMIT 1;
  END IF;
  IF event_count=0 AND NOT (NEW.sequence_no=1 AND NEW.event_type='ADMISSION_CREATED' AND NEW.from_status IS NULL AND NEW.to_status='CREATED' AND NEW.previous_event_hash IS NULL)
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_EVENT_SEQUENCE_INVALID'; END IF;
  IF event_count>0 AND (NEW.sequence_no<>event_count+1 OR NOT (NEW.previous_event_hash<=>latest_hash) OR NEW.from_status<>latest_status)
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_EVENT_CHAIN_INVALID'; END IF;
  IF latest_status IN ('REVOKED','EXPIRED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_TERMINAL_EVENT'; END IF;
  IF event_count>0 AND NOT (
       (latest_status='CREATED' AND NEW.to_status IN ('ELIGIBLE','BLOCKED','REJECTED'))
    OR (latest_status='ELIGIBLE' AND NEW.to_status IN ('APPROVED_FOR_EXECUTION','REVOKED','EXPIRED'))
    OR (latest_status='APPROVED_FOR_EXECUTION' AND NEW.to_status IN ('REVOKED','EXPIRED')))
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_EVENT_TRANSITION_INVALID'; END IF;
END$$
CREATE TRIGGER trg_role_admission_slot_update_guard BEFORE UPDATE ON workflow_role_runtime_execution_admission_slot
FOR EACH ROW BEGIN
  DECLARE matching_event INT DEFAULT 0;
  IF OLD.active_admission_row_id IS NULL AND NEW.active_admission_row_id IS NOT NULL THEN
    SELECT COUNT(*) INTO matching_event FROM workflow_role_runtime_execution_admission_event
     WHERE admission_row_id=NEW.active_admission_row_id AND event_type='APPROVED_FOR_EXECUTION';
  ELSEIF OLD.active_admission_row_id IS NOT NULL AND NEW.active_admission_row_id IS NULL THEN
    SELECT COUNT(*) INTO matching_event FROM workflow_role_runtime_execution_admission_event
     WHERE admission_row_id=OLD.active_admission_row_id AND event_type IN ('REVOKED','EXPIRED');
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_SLOT_CAS_ONLY';
  END IF;
  IF matching_event<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_ADMISSION_SLOT_EVENT_REQUIRED'; END IF;
END$$
DELIMITER ;

-- Phase 12: static final structure assertion after all permanent DDL.
CREATE TEMPORARY TABLE tmp_role_admission_v2615_final (
  assertion_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  violation_count BIGINT NOT NULL,
  CONSTRAINT chk_role_admission_v2615_final CHECK (violation_count=0));
INSERT INTO tmp_role_admission_v2615_final VALUES ('TARGET_TABLES',
  ABS(4-(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('workflow_role_runtime_execution_admission','workflow_role_runtime_execution_admission_evidence','workflow_role_runtime_execution_admission_event','workflow_role_runtime_execution_admission_slot'))));
INSERT INTO tmp_role_admission_v2615_final VALUES ('APPEND_TRIGGERS',
  ABS(9-(SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND trigger_name IN ('trg_role_admission_insert_guard','trg_role_admission_no_update','trg_role_admission_no_delete','trg_role_admission_evidence_no_update','trg_role_admission_evidence_no_delete','trg_role_admission_event_no_update','trg_role_admission_event_no_delete','trg_role_admission_event_insert_guard','trg_role_admission_slot_update_guard'))));
DROP TEMPORARY TABLE tmp_role_admission_v2615_final;
