-- Sprint 2-3.7-WF5.23: ROLE realtime eligibility evidence persistence.
-- Persistence only. This migration does not enable ROLE runtime or connect a Directory.
USE enterprise_platform;

-- Phase 1: fail before permanent DDL when the frozen parent contract is unavailable.
CREATE TEMPORARY TABLE tmp_v2616_guard (
    violation_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    violation_count BIGINT NOT NULL,
    PRIMARY KEY (violation_code),
    CONSTRAINT chk_tmp_v2616_guard CHECK (violation_count = 0)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

INSERT INTO tmp_v2616_guard
SELECT 'REQUIRED_PARENT_TABLES', ABS(9 - COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'workflow_instance','workflow_node_execution','workflow_task',
      'workflow_task_candidate_pool','workflow_task_candidate_member',
      'workflow_instance_resolver_binding_set','workflow_instance_resolver_binding',
      'workflow_node_resolver_binding_snapshot','workflow_task_claim'
  );

INSERT INTO tmp_v2616_guard
SELECT 'TARGET_TABLES_ALREADY_EXIST', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'workflow_role_realtime_eligibility_evidence',
      'workflow_role_realtime_eligibility_validator_evidence',
      'workflow_role_realtime_eligibility_capability_evidence',
      'workflow_role_realtime_eligibility_event'
  );

INSERT INTO tmp_v2616_guard
SELECT 'CLAIM_COLUMNS_ALREADY_EXIST', COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('workflow_task_claim','workflow_task_claim_audit')
  AND column_name IN ('eligibility_evidence_id','eligibility_contract_version','eligibility_persistence_hash');

INSERT INTO tmp_v2616_guard
SELECT 'REQUIRED_PARENT_KEYS', ABS(5 - COUNT(DISTINCT CONCAT(table_name,':',index_name)))
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN (
      'uk_workflow_task_claim_owner',
      'uk_workflow_candidate_pool_claim_owner',
      'uk_workflow_candidate_member_claim_owner',
      'uk_workflow_resolver_binding_owner',
      'uk_workflow_node_resolver_binding_owner'
  );

-- Phase 2: immutable aggregate header.
CREATE TABLE workflow_role_realtime_eligibility_evidence (
    id BIGINT NOT NULL,
    eligibility_evidence_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eligibility_request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no INT NOT NULL,
    correlation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    node_execution_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    candidate_pool_id BIGINT NOT NULL,
    candidate_member_id BIGINT NOT NULL,
    candidate_user_id BIGINT NOT NULL,
    binding_set_id BIGINT NOT NULL,
    resolver_binding_id BIGINT NOT NULL,
    node_resolver_binding_id BIGINT NOT NULL,
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_pool_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    runtime_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eligibility_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_directory_revision VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_directory_revision VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_complete SMALLINT NOT NULL,
    directory_effective_at DATETIME(3) NOT NULL,
    directory_checked_at DATETIME(3) NOT NULL,
    decision VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    terminal_validator_order INT NOT NULL,
    validator_count INT NOT NULL,
    capability_count INT NOT NULL,
    validator_evidence_root_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capability_evidence_root_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    persistence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eligibility_canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    persistence_canonical_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_source VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_at DATETIME(3) NOT NULL,
    verified_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_realtime_eligibility_business_id (eligibility_evidence_id),
    UNIQUE KEY uk_role_realtime_eligibility_request (eligibility_request_id),
    UNIQUE KEY uk_role_realtime_eligibility_attempt (
        task_id, candidate_user_id, claim_request_id, attempt_no
    ),
    UNIQUE KEY uk_role_realtime_eligibility_persistence_hash (persistence_hash),
    UNIQUE KEY uk_role_realtime_eligibility_owner (
        id, task_id, instance_id, node_execution_id, candidate_pool_id,
        candidate_member_id, candidate_user_id
    ),
    KEY idx_role_realtime_eligibility_task (task_id, candidate_user_id, verified_at),
    KEY idx_role_realtime_eligibility_expiry (decision, expires_at),
    KEY idx_role_realtime_eligibility_correlation (correlation_id),
    CONSTRAINT fk_role_realtime_eligibility_task
        FOREIGN KEY (task_id, instance_id, node_execution_id)
        REFERENCES workflow_task(id, instance_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_realtime_eligibility_pool
        FOREIGN KEY (candidate_pool_id, task_id, instance_id, node_execution_id)
        REFERENCES workflow_task_candidate_pool(id, task_id, instance_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_realtime_eligibility_member
        FOREIGN KEY (candidate_member_id, candidate_pool_id, task_id, instance_id, candidate_user_id)
        REFERENCES workflow_task_candidate_member(id, pool_id, task_id, instance_id, candidate_user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_realtime_eligibility_resolver
        FOREIGN KEY (resolver_binding_id, binding_set_id, instance_id)
        REFERENCES workflow_instance_resolver_binding(id, binding_set_id, instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_realtime_eligibility_node_resolver
        FOREIGN KEY (node_resolver_binding_id, instance_id, node_id, resolver_binding_id)
        REFERENCES workflow_node_resolver_binding_snapshot(id, instance_id, node_id, resolver_binding_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_realtime_eligibility_identity CHECK (
        attempt_no > 0 AND candidate_user_id > 0
        AND CHAR_LENGTH(TRIM(eligibility_evidence_id)) > 0
        AND CHAR_LENGTH(TRIM(eligibility_request_id)) > 0
        AND CHAR_LENGTH(TRIM(claim_request_id)) > 0
        AND CHAR_LENGTH(TRIM(claim_idempotency_key)) > 0
        AND CHAR_LENGTH(TRIM(correlation_id)) > 0
    ),
    CONSTRAINT chk_role_realtime_eligibility_codes CHECK (
        role_code REGEXP '^[A-Z0-9_:-]+$'
        AND CHAR_LENGTH(TRIM(organization_id)) > 0
        AND CHAR_LENGTH(TRIM(candidate_directory_revision)) > 0
        AND CHAR_LENGTH(TRIM(claim_directory_revision)) > 0
    ),
    CONSTRAINT chk_role_realtime_eligibility_hashes CHECK (
        candidate_pool_hash REGEXP '^[0-9a-f]{64}$'
        AND runtime_binding_hash REGEXP '^[0-9a-f]{64}$'
        AND eligibility_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_result_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND validator_evidence_root_hash REGEXP '^[0-9a-f]{64}$'
        AND capability_evidence_root_hash REGEXP '^[0-9a-f]{64}$'
        AND persistence_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_role_realtime_eligibility_decision CHECK (
        decision IN ('ELIGIBLE','INELIGIBLE','INDETERMINATE')
        AND terminal_validator_order BETWEEN 1 AND 27
        AND validator_count = terminal_validator_order
        AND capability_count BETWEEN 0 AND 10
        AND (decision <> 'ELIGIBLE'
             OR (terminal_validator_order = 27 AND validator_count = 27 AND capability_count = 10))
    ),
    CONSTRAINT chk_role_realtime_eligibility_directory CHECK (directory_complete IN (0,1)),
    CONSTRAINT chk_role_realtime_eligibility_time CHECK (
        directory_checked_at <= verified_at
        AND directory_effective_at <= claim_at
        AND verified_at <= claim_at
        AND claim_at < expires_at
    ),
    CONSTRAINT chk_role_realtime_eligibility_canonical CHECK (
        eligibility_canonical_version = 'ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1'
        AND persistence_canonical_version = 'ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1'
        AND CHAR_LENGTH(TRIM(policy_version)) > 0
        AND evidence_source IN ('CONTRACT_TEST','EXTERNAL_AUTHORITY')
    ),
    CONSTRAINT chk_role_realtime_eligibility_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 3: ordered validator evidence.
CREATE TABLE workflow_role_realtime_eligibility_validator_evidence (
    id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    validator_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    validator_order INT NOT NULL,
    status VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_realtime_validator_order (evidence_id, validator_order),
    UNIQUE KEY uk_role_realtime_validator_code (evidence_id, validator_code),
    KEY idx_role_realtime_validator_status (validator_code, status),
    CONSTRAINT fk_role_realtime_validator_evidence FOREIGN KEY (evidence_id)
        REFERENCES workflow_role_realtime_eligibility_evidence(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_realtime_validator_order CHECK (validator_order BETWEEN 1 AND 27),
    CONSTRAINT chk_role_realtime_validator_status CHECK (status IN ('PASS','FAIL','INDETERMINATE')),
    CONSTRAINT chk_role_realtime_validator_data CHECK (
        CHAR_LENGTH(TRIM(validator_code)) > 0
        AND CHAR_LENGTH(TRIM(reason_code)) > 0
        AND evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND canonical_version = 'ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1'
    ),
    CONSTRAINT chk_role_realtime_validator_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 4: deduplicated realtime capability evidence.
CREATE TABLE workflow_role_realtime_eligibility_capability_evidence (
    id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    capability_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    validator_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_version VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_realtime_capability_code (evidence_id, capability_code),
    KEY idx_role_realtime_capability_status (capability_code, status, decision),
    CONSTRAINT fk_role_realtime_capability_evidence FOREIGN KEY (evidence_id)
        REFERENCES workflow_role_realtime_eligibility_evidence(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_realtime_capability_code CHECK (
        capability_code IN (
            'ROLE_MEMBERSHIP','USER_STATUS','ORGANIZATION_MEMBERSHIP','DATA_SCOPE',
            'PLATFORM_SOD','BUSINESS_SOD','AUDIT','FEATURE_FLAG','KILL_SWITCH','CANARY'
        )
    ),
    CONSTRAINT chk_role_realtime_capability_status CHECK (
        status IN ('PASS','DENY','INDETERMINATE')
        AND decision IN ('PASS','DENY','INDETERMINATE')
    ),
    CONSTRAINT chk_role_realtime_capability_data CHECK (
        CHAR_LENGTH(TRIM(validator_code)) > 0
        AND CHAR_LENGTH(TRIM(provider_version)) > 0
        AND CHAR_LENGTH(TRIM(policy_version)) > 0
        AND evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND canonical_version = 'ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1'
        AND (valid_until IS NULL OR valid_until > checked_at)
    ),
    CONSTRAINT chk_role_realtime_capability_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 5: append-only lifecycle and claim outcome chain.
CREATE TABLE workflow_role_realtime_eligibility_event (
    id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_id BIGINT NULL,
    reason_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    operator_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_realtime_event_sequence (evidence_id, sequence_no),
    UNIQUE KEY uk_role_realtime_event_idempotency (evidence_id, idempotency_key),
    KEY idx_role_realtime_event_claim (claim_id, occurred_at),
    CONSTRAINT fk_role_realtime_event_evidence FOREIGN KEY (evidence_id)
        REFERENCES workflow_role_realtime_eligibility_evidence(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_realtime_event_claim FOREIGN KEY (claim_id)
        REFERENCES workflow_task_claim(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_realtime_event_type CHECK (
        event_type IN ('PREPARED','VERIFIED','CONSUMED','EXPIRED','REJECTED','CLAIM_NOT_COMMITTED')
    ),
    CONSTRAINT chk_role_realtime_event_data CHECK (
        sequence_no > 0 AND CHAR_LENGTH(TRIM(reason_code)) > 0
        AND CHAR_LENGTH(TRIM(operator_id)) > 0
        AND CHAR_LENGTH(TRIM(idempotency_key)) > 0
        AND event_hash REGEXP '^[0-9a-f]{64}$'
        AND (previous_event_hash IS NULL OR previous_event_hash REGEXP '^[0-9a-f]{64}$')
        AND canonical_version = 'ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1'
        AND ((event_type = 'CONSUMED' AND claim_id IS NOT NULL)
             OR (event_type <> 'CONSUMED' AND claim_id IS NULL))
    ),
    CONSTRAINT chk_role_realtime_event_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 6: one-way Evidence -> Claim binding. Existing rows remain LEGACY and NULL.
ALTER TABLE workflow_task_claim
    ADD COLUMN eligibility_evidence_id BIGINT NULL AFTER eligibility_snapshot_hash,
    ADD COLUMN eligibility_contract_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER eligibility_evidence_id,
    ADD UNIQUE KEY uk_workflow_task_claim_eligibility_evidence (eligibility_evidence_id),
    ADD CONSTRAINT fk_workflow_task_claim_eligibility_owner
        FOREIGN KEY (
            eligibility_evidence_id, task_id, instance_id, node_execution_id,
            candidate_pool_id, candidate_member_id, candidate_user_id
        ) REFERENCES workflow_role_realtime_eligibility_evidence (
            id, task_id, instance_id, node_execution_id,
            candidate_pool_id, candidate_member_id, candidate_user_id
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_workflow_task_claim_eligibility_binding CHECK (
        (eligibility_evidence_id IS NULL AND eligibility_contract_version IS NULL)
        OR (eligibility_evidence_id IS NOT NULL
            AND eligibility_contract_version = 'ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1')
    );

ALTER TABLE workflow_task_claim_audit
    ADD COLUMN eligibility_evidence_id BIGINT NULL AFTER frozen_eligibility_hash,
    ADD COLUMN eligibility_persistence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER eligibility_evidence_id,
    ADD KEY idx_workflow_task_claim_audit_eligibility (eligibility_evidence_id, event_time),
    ADD CONSTRAINT fk_workflow_task_claim_audit_eligibility FOREIGN KEY (eligibility_evidence_id)
        REFERENCES workflow_role_realtime_eligibility_evidence(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_workflow_task_claim_audit_eligibility_hash CHECK (
        (eligibility_evidence_id IS NULL AND eligibility_persistence_hash IS NULL)
        OR (eligibility_evidence_id IS NOT NULL
            AND eligibility_persistence_hash REGEXP '^[0-9a-f]{64}$')
    );

-- Phase 7/8: append-only protection, lifecycle checks and final Claim database gate.
DELIMITER $$
CREATE TRIGGER trg_role_realtime_eligibility_no_update
BEFORE UPDATE ON workflow_role_realtime_eligibility_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_ELIGIBILITY_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_eligibility_no_delete
BEFORE DELETE ON workflow_role_realtime_eligibility_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_ELIGIBILITY_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_validator_no_update
BEFORE UPDATE ON workflow_role_realtime_eligibility_validator_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_VALIDATOR_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_validator_no_delete
BEFORE DELETE ON workflow_role_realtime_eligibility_validator_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_VALIDATOR_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_validator_insert_guard
BEFORE INSERT ON workflow_role_realtime_eligibility_validator_evidence
FOR EACH ROW
BEGIN
    DECLARE actual_hash VARCHAR(64);
    SET actual_hash=SHA2(CONCAT(
        'ROLE_REALTIME_VALIDATOR_EVIDENCE_V1',
        CHAR_LENGTH(CAST(NEW.validator_order AS CHAR)),':',NEW.validator_order,
        CHAR_LENGTH(NEW.validator_code),':',NEW.validator_code,
        CHAR_LENGTH(NEW.status),':',NEW.status,
        CHAR_LENGTH(NEW.reason_code),':',NEW.reason_code,
        DATE_FORMAT(NEW.checked_at,'%Y-%m-%dT%H:%i:%s.%fZ')
    ),256);
    IF actual_hash<>NEW.evidence_hash THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_VALIDATOR_HASH_MISMATCH';
    END IF;
END$$
CREATE TRIGGER trg_role_realtime_capability_no_update
BEFORE UPDATE ON workflow_role_realtime_eligibility_capability_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CAPABILITY_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_capability_no_delete
BEFORE DELETE ON workflow_role_realtime_eligibility_capability_evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CAPABILITY_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_capability_insert_guard
BEFORE INSERT ON workflow_role_realtime_eligibility_capability_evidence
FOR EACH ROW
BEGIN
    DECLARE actual_hash VARCHAR(64);
    SET actual_hash=SHA2(CONCAT(
        'ROLE_REALTIME_CAPABILITY_EVIDENCE_V1',
        CHAR_LENGTH(NEW.capability_code),':',NEW.capability_code,
        CHAR_LENGTH(NEW.validator_code),':',NEW.validator_code,
        CHAR_LENGTH(NEW.status),':',NEW.status,
        CHAR_LENGTH(NEW.decision),':',NEW.decision,
        CHAR_LENGTH(NEW.provider_version),':',NEW.provider_version,
        CHAR_LENGTH(NEW.policy_version),':',NEW.policy_version,
        DATE_FORMAT(NEW.checked_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
        IFNULL(DATE_FORMAT(NEW.valid_until,'%Y-%m-%dT%H:%i:%s.%fZ'),'null')
    ),256);
    IF actual_hash<>NEW.evidence_hash THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CAPABILITY_HASH_MISMATCH';
    END IF;
END$$
CREATE TRIGGER trg_role_realtime_event_no_update
BEFORE UPDATE ON workflow_role_realtime_eligibility_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_APPEND_ONLY'$$
CREATE TRIGGER trg_role_realtime_event_no_delete
BEFORE DELETE ON workflow_role_realtime_eligibility_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_APPEND_ONLY'$$

CREATE TRIGGER trg_role_realtime_event_insert_guard
BEFORE INSERT ON workflow_role_realtime_eligibility_event
FOR EACH ROW
BEGIN
    DECLARE previous_type VARCHAR(30);
    DECLARE previous_hash VARCHAR(64);
    DECLARE actual_event_hash VARCHAR(64);
    DECLARE event_count INT DEFAULT 0;
    SELECT COUNT(*), MAX(event_type), MAX(event_hash)
      INTO event_count, previous_type, previous_hash
      FROM workflow_role_realtime_eligibility_event
     WHERE evidence_id = NEW.evidence_id;
    IF NEW.sequence_no <> event_count + 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_SEQUENCE_INVALID';
    END IF;
    IF event_count = 0 THEN
        IF NEW.event_type NOT IN ('PREPARED','REJECTED') OR NEW.previous_event_hash IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_INITIAL_INVALID';
        END IF;
    ELSE
        IF NOT (NEW.previous_event_hash <=> previous_hash) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_CHAIN_INVALID';
        END IF;
        IF (previous_type = 'PREPARED' AND NEW.event_type NOT IN ('VERIFIED','REJECTED','EXPIRED','CLAIM_NOT_COMMITTED'))
           OR (previous_type = 'VERIFIED' AND NEW.event_type NOT IN ('CONSUMED','EXPIRED','CLAIM_NOT_COMMITTED'))
           OR previous_type IN ('CONSUMED','REJECTED','EXPIRED') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_TRANSITION_INVALID';
        END IF;
    END IF;
    SET actual_event_hash=SHA2(CONCAT(
        'ROLE_REALTIME_ELIGIBILITY_EVENT_V1',
        CHAR_LENGTH(CAST(NEW.evidence_id AS CHAR)),':',NEW.evidence_id,
        CHAR_LENGTH(CAST(NEW.sequence_no AS CHAR)),':',NEW.sequence_no,
        CHAR_LENGTH(NEW.event_type),':',NEW.event_type,
        IFNULL(CONCAT(CHAR_LENGTH(CAST(NEW.claim_id AS CHAR)),':',NEW.claim_id),'null'),
        CHAR_LENGTH(NEW.reason_code),':',NEW.reason_code,
        IFNULL(NEW.previous_event_hash,'null'),
        DATE_FORMAT(NEW.occurred_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
        CHAR_LENGTH(NEW.operator_id),':',NEW.operator_id,
        CHAR_LENGTH(NEW.idempotency_key),':',NEW.idempotency_key
    ),256);
    IF actual_event_hash<>NEW.event_hash THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_EVENT_HASH_MISMATCH';
    END IF;
END$$

CREATE TRIGGER trg_workflow_task_claim_role_eligibility_gate
BEFORE INSERT ON workflow_task_claim
FOR EACH ROW
BEGIN
    DECLARE pool_resolver VARCHAR(64);
    DECLARE pool_strategy VARCHAR(30);
    DECLARE matches INT DEFAULT 0;
    DECLARE validator_matches INT DEFAULT 0;
    DECLARE capability_matches INT DEFAULT 0;
    DECLARE verified_events INT DEFAULT 0;
    DECLARE actual_validator_root VARCHAR(64);
    DECLARE actual_capability_root VARCHAR(64);
    DECLARE actual_persistence_hash VARCHAR(64);
    DECLARE stored_validator_root VARCHAR(64);
    DECLARE stored_capability_root VARCHAR(64);
    DECLARE stored_persistence_hash VARCHAR(64);
    SELECT resolver_code, strategy_type INTO pool_resolver, pool_strategy
      FROM workflow_task_candidate_pool
     WHERE id=NEW.candidate_pool_id AND task_id=NEW.task_id AND instance_id=NEW.instance_id;
    IF pool_strategy='ROLE' AND pool_resolver='ROLE_DIRECTORY_V1' THEN
        IF NEW.eligibility_evidence_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_ELIGIBILITY_REQUIRED';
        END IF;
    END IF;
    IF NEW.eligibility_evidence_id IS NOT NULL THEN
        SELECT COUNT(*) INTO matches
          FROM workflow_role_realtime_eligibility_evidence e
         WHERE e.id=NEW.eligibility_evidence_id
           AND e.task_id=NEW.task_id AND e.instance_id=NEW.instance_id
           AND e.node_execution_id=NEW.node_execution_id
           AND e.candidate_pool_id=NEW.candidate_pool_id
           AND e.candidate_member_id=NEW.candidate_member_id
           AND e.candidate_user_id=NEW.candidate_user_id
           AND e.decision='ELIGIBLE' AND e.directory_complete=1
           AND e.validator_count=27 AND e.capability_count=10
           AND NEW.claim_time>=e.verified_at AND NEW.claim_time<e.expires_at
           AND CURRENT_TIMESTAMP(3)<e.expires_at
           AND NEW.eligibility_snapshot_hash=e.eligibility_hash;
        IF matches<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_ELIGIBILITY_INVALID';
        END IF;
        SELECT COUNT(*) INTO validator_matches
          FROM workflow_role_realtime_eligibility_validator_evidence
         WHERE evidence_id=NEW.eligibility_evidence_id AND status='PASS';
        SELECT COUNT(*) INTO capability_matches
          FROM workflow_role_realtime_eligibility_capability_evidence
         WHERE evidence_id=NEW.eligibility_evidence_id AND status='PASS' AND decision='PASS';
        SELECT COUNT(*) INTO verified_events
          FROM workflow_role_realtime_eligibility_event
         WHERE evidence_id=NEW.eligibility_evidence_id AND event_type='VERIFIED';
        IF validator_matches<>27 OR capability_matches<>10 OR verified_events<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_ELIGIBILITY_EVIDENCE_INCOMPLETE';
        END IF;
        SELECT SHA2(CONCAT('ROLE_REALTIME_VALIDATOR_ROOT_V1',
                   GROUP_CONCAT(UNHEX(evidence_hash) ORDER BY validator_order SEPARATOR '')),256)
          INTO actual_validator_root
          FROM workflow_role_realtime_eligibility_validator_evidence
         WHERE evidence_id=NEW.eligibility_evidence_id;
        SELECT SHA2(CONCAT('ROLE_REALTIME_CAPABILITY_ROOT_V1',
                   GROUP_CONCAT(UNHEX(evidence_hash) ORDER BY capability_code SEPARATOR '')),256)
          INTO actual_capability_root
          FROM workflow_role_realtime_eligibility_capability_evidence
         WHERE evidence_id=NEW.eligibility_evidence_id;
        SELECT validator_evidence_root_hash,capability_evidence_root_hash,persistence_hash,
               SHA2(CONCAT(
                   'ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1',
                   CHAR_LENGTH(eligibility_evidence_id),':',eligibility_evidence_id,
                   CHAR_LENGTH(CAST(task_id AS CHAR)),':',task_id,
                   CHAR_LENGTH(CAST(instance_id AS CHAR)),':',instance_id,
                   CHAR_LENGTH(CAST(node_execution_id AS CHAR)),':',node_execution_id,
                   CHAR_LENGTH(CAST(candidate_pool_id AS CHAR)),':',candidate_pool_id,
                   CHAR_LENGTH(CAST(candidate_member_id AS CHAR)),':',candidate_member_id,
                   CHAR_LENGTH(CAST(candidate_user_id AS CHAR)),':',candidate_user_id,
                   eligibility_hash,candidate_pool_hash,runtime_binding_hash,
                   CHAR_LENGTH(role_code),':',role_code,
                   CHAR_LENGTH(organization_id),':',organization_id,
                   CHAR_LENGTH(candidate_directory_revision),':',candidate_directory_revision,
                   CHAR_LENGTH(claim_directory_revision),':',claim_directory_revision,
                   directory_result_hash,validator_evidence_root_hash,capability_evidence_root_hash,
                   DATE_FORMAT(verified_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
                   DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
                   CHAR_LENGTH(decision),':',decision,
                   CHAR_LENGTH(policy_version),':',policy_version
               ),256)
          INTO stored_validator_root,stored_capability_root,stored_persistence_hash,actual_persistence_hash
          FROM workflow_role_realtime_eligibility_evidence
         WHERE id=NEW.eligibility_evidence_id;
        IF actual_validator_root<>stored_validator_root THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_VALIDATOR_ROOT_MISMATCH';
        END IF;
        IF actual_capability_root<>stored_capability_root THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CAPABILITY_ROOT_MISMATCH';
        END IF;
        IF actual_persistence_hash<>stored_persistence_hash THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_PERSISTENCE_HASH_MISMATCH';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_workflow_task_claim_eligibility_binding_immutable
BEFORE UPDATE ON workflow_task_claim
FOR EACH ROW
BEGIN
    IF NOT (NEW.eligibility_evidence_id <=> OLD.eligibility_evidence_id)
       OR NOT (NEW.eligibility_contract_version <=> OLD.eligibility_contract_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CLAIM_BINDING_IMMUTABLE';
    END IF;
END$$

CREATE TRIGGER trg_workflow_task_claim_audit_eligibility_gate
BEFORE INSERT ON workflow_task_claim_audit
FOR EACH ROW
BEGIN
    DECLARE matches INT DEFAULT 0;
    IF NEW.result='SUCCESS' AND NEW.claim_id IS NOT NULL THEN
        SELECT COUNT(*) INTO matches
          FROM workflow_task_claim c
          LEFT JOIN workflow_role_realtime_eligibility_evidence e
            ON e.id=c.eligibility_evidence_id
         WHERE c.id=NEW.claim_id
           AND ((c.eligibility_evidence_id IS NULL
                 AND NEW.eligibility_evidence_id IS NULL
                 AND NEW.eligibility_persistence_hash IS NULL)
                OR (c.eligibility_evidence_id=NEW.eligibility_evidence_id
                    AND e.persistence_hash=NEW.eligibility_persistence_hash));
        IF matches<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ROLE_REALTIME_CLAIM_AUDIT_BINDING_INVALID';
        END IF;
    END IF;
END$$
DELIMITER ;

-- Phase 9: all permanent objects must exist after migration.
CREATE TEMPORARY TABLE tmp_v2616_final (
    assertion_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_tmp_v2616_final CHECK (violation_count=0)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
INSERT INTO tmp_v2616_final
SELECT 'TARGET_TABLES', ABS(4-COUNT(*)) FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN (
   'workflow_role_realtime_eligibility_evidence',
   'workflow_role_realtime_eligibility_validator_evidence',
   'workflow_role_realtime_eligibility_capability_evidence',
   'workflow_role_realtime_eligibility_event');
INSERT INTO tmp_v2616_final
SELECT 'TARGET_TRIGGERS', ABS(14-COUNT(*)) FROM information_schema.triggers
 WHERE trigger_schema=DATABASE() AND trigger_name IN (
   'trg_role_realtime_eligibility_no_update','trg_role_realtime_eligibility_no_delete',
   'trg_role_realtime_validator_no_update','trg_role_realtime_validator_no_delete',
   'trg_role_realtime_validator_insert_guard',
   'trg_role_realtime_capability_no_update','trg_role_realtime_capability_no_delete',
   'trg_role_realtime_capability_insert_guard',
   'trg_role_realtime_event_no_update','trg_role_realtime_event_no_delete',
   'trg_role_realtime_event_insert_guard','trg_workflow_task_claim_role_eligibility_gate',
   'trg_workflow_task_claim_eligibility_binding_immutable',
   'trg_workflow_task_claim_audit_eligibility_gate');
DROP TEMPORARY TABLE tmp_v2616_final;
DROP TEMPORARY TABLE tmp_v2616_guard;
