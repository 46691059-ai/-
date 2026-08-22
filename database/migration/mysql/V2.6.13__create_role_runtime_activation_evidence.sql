-- Sprint 2-3.7-WF5.10.3: redesigned candidate after archived MySQL 6125 evidence.
-- ROLE Runtime, ROLE Tasks, Candidate Pools, Claims and Investment integration remain disabled.
USE enterprise_platform;

-- All checks that can fail before target DDL are evaluated in one temporary guard.
-- No target data is repaired, deleted, inferred or backfilled.
CREATE TEMPORARY TABLE tmp_role_activation_v2613_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_activation_v2613_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_activation_v2613_guard (violation_count)
SELECT
    ABS(2 - (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name IN ('role_runtime_binding_approval',
                                  'workflow_role_runtime_binding_snapshot')
               AND column_name = 'resolver_version'
               AND character_set_name = 'ascii'
               AND collation_name = 'ascii_bin'
               AND is_nullable = 'NO'))
  + (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE()
         AND table_name IN ('role_runtime_activation_request',
                            'role_runtime_activation_approval',
                            'role_runtime_activation_evidence'))
  + (SELECT COUNT(*) FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table IN ('role_runtime_activation_request',
                                    'role_runtime_activation_approval',
                                    'role_runtime_activation_evidence'));

DROP TEMPORARY TABLE tmp_role_activation_v2613_guard;

-- Base tables first. Parent business keys are declared before any FK is added.
CREATE TABLE role_runtime_activation_request (
    id BIGINT NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision BIGINT NOT NULL,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_scope VARCHAR(200) NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    status VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_activation_owner (activation_id, delete_token),
    KEY idx_role_activation_hash (activation_hash, delete_token),
    KEY idx_role_activation_resolver (resolver_code, resolver_version, status),
    KEY idx_role_activation_effective (effective_at, status),
    CONSTRAINT chk_role_activation_resolver CHECK (
        REGEXP_LIKE(resolver_code, '^[A-Z0-9_]+$', 'c')
        AND REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')),
    CONSTRAINT chk_role_activation_hashes CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'
        AND candidate_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND activation_hash REGEXP '^[0-9a-f]{64}$'
        AND approval_evidence_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_activation_revision CHECK (directory_revision >= 0),
    CONSTRAINT chk_role_activation_scope CHECK (CHAR_LENGTH(TRIM(business_scope)) > 0),
    CONSTRAINT chk_role_activation_status CHECK (status IN (
        'DRAFT','SUBMITTED','APPROVAL_PENDING','APPROVED','PERSISTED',
        'REJECTED','REVOKED','BLOCKED')),
    CONSTRAINT chk_role_activation_deleted CHECK (deleted = 0 AND delete_token = 0),
    CONSTRAINT chk_role_activation_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only ROLE Runtime activation request and persistence fact';

CREATE TABLE role_runtime_activation_approval (
    id BIGINT NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approver_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approver_id VARCHAR(100) NOT NULL,
    decision VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(300) NOT NULL,
    source_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision_time DATETIME(3) NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_activation_approval_owner (id, activation_id, delete_token),
    UNIQUE KEY uk_role_activation_approver (activation_id, approver_type, delete_token),
    UNIQUE KEY uk_role_activation_decision_hash (decision_hash, delete_token),
    KEY idx_role_activation_decision_time (decision_time),
    CONSTRAINT chk_role_activation_approver_type CHECK (approver_type IN (
        'BUSINESS_OWNER','SECURITY_AUDIT','RELEASE_APPROVER')),
    CONSTRAINT chk_role_activation_decision CHECK (decision IN ('APPROVE','REJECT','REVOKE')),
    CONSTRAINT chk_role_activation_approval_hashes CHECK (
        source_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND decision_hash REGEXP '^[0-9a-f]{64}$'
        AND activation_hash REGEXP '^[0-9a-f]{64}$'
        AND contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_activation_approval_deleted CHECK (deleted = 0 AND delete_token = 0),
    CONSTRAINT chk_role_activation_approval_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only ROLE Runtime activation RACI decisions';

CREATE TABLE role_runtime_activation_evidence (
    id BIGINT NOT NULL,
    approval_id BIGINT NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_activation_evidence_type (
        approval_id, activation_id, evidence_type, delete_token),
    KEY idx_role_activation_evidence_hash (evidence_hash),
    KEY idx_role_activation_evidence_owner (activation_id, delete_token),
    CONSTRAINT chk_role_activation_evidence_type CHECK (evidence_type IN (
        'ACTIVATION_APPROVAL','RESOLVER_CONTRACT','BINDING','CANDIDATE','DIRECTORY')),
    CONSTRAINT chk_role_activation_evidence_hashes CHECK (
        evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND activation_hash REGEXP '^[0-9a-f]{64}$'
        AND contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_activation_canonical CHECK (
        canonical_version = 'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1'),
    CONSTRAINT chk_role_activation_evidence_deleted CHECK (deleted = 0 AND delete_token = 0),
    CONSTRAINT chk_role_activation_evidence_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only hash-only activation evidence bound to one approval';

-- Add FKs only after every referenced UNIQUE key exists.
ALTER TABLE role_runtime_activation_approval
    ADD CONSTRAINT fk_role_activation_approval_request
    FOREIGN KEY (activation_id, delete_token)
    REFERENCES role_runtime_activation_request(activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE role_runtime_activation_evidence
    ADD CONSTRAINT fk_role_activation_evidence_request
    FOREIGN KEY (activation_id, delete_token)
    REFERENCES role_runtime_activation_request(activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_activation_evidence_approval
    FOREIGN KEY (approval_id, activation_id, delete_token)
    REFERENCES role_runtime_activation_approval(id, activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

DELIMITER $$

CREATE TRIGGER trg_role_activation_approval_insert_guard
BEFORE INSERT ON role_runtime_activation_approval
FOR EACH ROW
BEGIN
    DECLARE request_activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE request_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE request_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SELECT activation_hash, contract_hash, binding_hash
      INTO request_activation_hash, request_contract_hash, request_binding_hash
      FROM role_runtime_activation_request
     WHERE activation_id = NEW.activation_id AND delete_token = NEW.delete_token
     LIMIT 1;

    IF request_activation_hash IS NULL
       OR request_activation_hash <> NEW.activation_hash
       OR request_contract_hash <> NEW.contract_hash
       OR request_binding_hash <> NEW.binding_hash THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPROVAL_HASH_MISMATCH';
    END IF;
END$$

CREATE TRIGGER trg_role_activation_evidence_insert_guard
BEFORE INSERT ON role_runtime_activation_evidence
FOR EACH ROW
BEGIN
    DECLARE approval_activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE approval_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE approval_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SELECT activation_hash, contract_hash, binding_hash
      INTO approval_activation_hash, approval_contract_hash, approval_binding_hash
      FROM role_runtime_activation_approval
     WHERE id = NEW.approval_id
       AND activation_id = NEW.activation_id
       AND delete_token = NEW.delete_token
     LIMIT 1;

    IF approval_activation_hash IS NULL
       OR approval_activation_hash <> NEW.activation_hash
       OR approval_contract_hash <> NEW.contract_hash
       OR approval_binding_hash <> NEW.binding_hash THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_EVIDENCE_HASH_MISMATCH';
    END IF;
END$$

CREATE TRIGGER trg_role_activation_request_no_update
BEFORE UPDATE ON role_runtime_activation_request
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPEND_ONLY';
END$$
CREATE TRIGGER trg_role_activation_request_no_delete
BEFORE DELETE ON role_runtime_activation_request
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPEND_ONLY';
END$$
CREATE TRIGGER trg_role_activation_approval_no_update
BEFORE UPDATE ON role_runtime_activation_approval
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPROVAL_APPEND_ONLY';
END$$
CREATE TRIGGER trg_role_activation_approval_no_delete
BEFORE DELETE ON role_runtime_activation_approval
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPROVAL_APPEND_ONLY';
END$$
CREATE TRIGGER trg_role_activation_evidence_no_update
BEFORE UPDATE ON role_runtime_activation_evidence
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_EVIDENCE_APPEND_ONLY';
END$$
CREATE TRIGGER trg_role_activation_evidence_no_delete
BEFORE DELETE ON role_runtime_activation_evidence
FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_EVIDENCE_APPEND_ONLY';
END$$

DELIMITER ;
