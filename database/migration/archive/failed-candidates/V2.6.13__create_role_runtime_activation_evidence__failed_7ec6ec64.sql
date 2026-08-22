-- Sprint 2-3.7-WF5.10: persistent ROLE Runtime activation approval evidence only.
-- This candidate does not enable ROLE Runtime or create Task/Candidate Pool/Claim objects.
USE enterprise_platform;

-- Fail before permanent DDL on an incomplete V2.6.12 chain or a partial prior attempt.
CREATE TEMPORARY TABLE tmp_role_activation_v2613_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_activation_v2613_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_activation_v2613_guard (violation_count)
SELECT
    ABS(2 - (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name IN ('role_runtime_binding_approval', 'workflow_role_runtime_binding_snapshot')
               AND column_name = 'resolver_version'
               AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'))
  + (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE()
         AND table_name IN ('role_runtime_activation_request',
                            'role_runtime_activation_approval',
                            'role_runtime_activation_evidence'));

DROP TEMPORARY TABLE tmp_role_activation_v2613_guard;

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
    UNIQUE KEY uk_role_activation_id (activation_id, delete_token),
    UNIQUE KEY uk_role_activation_hash (activation_hash, delete_token),
    KEY idx_role_activation_resolver (resolver_code, resolver_version, status),
    KEY idx_role_activation_effective (effective_at, status),
    CONSTRAINT chk_role_activation_resolver CHECK (
        REGEXP_LIKE(resolver_code, '^[A-Z0-9_]+$', 'c')
        AND REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')),
    CONSTRAINT chk_role_activation_hashes CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$' AND binding_hash REGEXP '^[0-9a-f]{64}$'
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
  COMMENT='Append-only ROLE Runtime activation request and final persistence fact';

CREATE TABLE role_runtime_activation_approval (
    id BIGINT NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approver_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approver_id VARCHAR(100) NOT NULL,
    decision VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(300) NOT NULL,
    source_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    UNIQUE KEY uk_role_activation_approver (activation_id, approver_type, delete_token),
    UNIQUE KEY uk_role_activation_decision_hash (decision_hash, delete_token),
    KEY idx_role_activation_decision_time (decision_time),
    CONSTRAINT fk_role_activation_approval_request FOREIGN KEY (activation_id)
        REFERENCES role_runtime_activation_request(activation_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_activation_approver_type CHECK (approver_type IN (
        'BUSINESS_OWNER','SECURITY_AUDIT','RELEASE_APPROVER')),
    CONSTRAINT chk_role_activation_decision CHECK (decision IN ('APPROVE','REJECT','REVOKE')),
    CONSTRAINT chk_role_activation_decision_hashes CHECK (
        source_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND decision_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_activation_approval_deleted CHECK (deleted = 0 AND delete_token = 0),
    CONSTRAINT chk_role_activation_approval_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only ROLE Runtime activation RACI decisions';

CREATE TABLE role_runtime_activation_evidence (
    id BIGINT NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    UNIQUE KEY uk_role_activation_evidence_type (activation_id, evidence_type, delete_token),
    KEY idx_role_activation_evidence_hash (evidence_hash),
    CONSTRAINT fk_role_activation_evidence_request FOREIGN KEY (activation_id)
        REFERENCES role_runtime_activation_request(activation_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_activation_evidence_type CHECK (evidence_type IN (
        'ACTIVATION_APPROVAL','RESOLVER_CONTRACT','BINDING','CANDIDATE','DIRECTORY')),
    CONSTRAINT chk_role_activation_evidence_hash CHECK (evidence_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_activation_canonical CHECK (
        canonical_version = 'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1'),
    CONSTRAINT chk_role_activation_evidence_deleted CHECK (deleted = 0 AND delete_token = 0),
    CONSTRAINT chk_role_activation_evidence_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only hash-only activation evidence; contains no directory or candidate members';

DELIMITER $$
CREATE TRIGGER trg_role_activation_request_no_update BEFORE UPDATE ON role_runtime_activation_request
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_activation_request_no_delete BEFORE DELETE ON role_runtime_activation_request
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_activation_approval_no_update BEFORE UPDATE ON role_runtime_activation_approval
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPROVAL_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_activation_approval_no_delete BEFORE DELETE ON role_runtime_activation_approval
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_APPROVAL_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_activation_evidence_no_update BEFORE UPDATE ON role_runtime_activation_evidence
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_EVIDENCE_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_activation_evidence_no_delete BEFORE DELETE ON role_runtime_activation_evidence
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'ROLE_RUNTIME_ACTIVATION_EVIDENCE_APPEND_ONLY'; END$$
DELIMITER ;
