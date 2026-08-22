. : File C:\Users\WUKONG\Documents\WindowsPowerShell\profile.ps1 cannot be loaded because running scripts is disabled o
n this system. For more information, see about_Execution_Policies at https:/go.microsoft.com/fwlink/?LinkID=135170.
At line:1 char:3
+ . 'C:\Users\WUKONG\Documents\WindowsPowerShell\profile.ps1'
+   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : SecurityError: (:) [], PSSecurityException
    + FullyQualifiedErrorId : UnauthorizedAccess
-- Sprint 2-3.7-WF5.14: pre-runtime ROLE binding persistence foundation.
-- This migration persists governance evidence only. It does not enable ROLE runtime,
-- create Workflow tasks, candidate pools or claims, or modify Investment data.
USE enterprise_platform;

-- Fail before permanent DDL when the immutable V2.6.13 parent contract is unavailable
-- or any target object already exists. No data is repaired, inferred or deleted.
CREATE TEMPORARY TABLE tmp_role_binding_v2614_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_binding_v2614_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_binding_v2614_guard (violation_count)
SELECT
    ABS(3 - (SELECT COUNT(*) FROM information_schema.tables
              WHERE table_schema = DATABASE()
                AND table_name IN ('role_runtime_activation_request',
                                   'role_runtime_activation_approval',
                                   'role_runtime_activation_evidence')))
  + ABS(2 - (SELECT COUNT(*) FROM information_schema.statistics
              WHERE table_schema = DATABASE()
                AND ((table_name = 'role_runtime_activation_request'
                      AND index_name = 'uk_role_activation_owner')
                  OR (table_name = 'role_runtime_activation_approval'
                      AND index_name = 'uk_role_activation_approval_owner'))
                AND seq_in_index = 1))
  + (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE()
         AND table_name IN ('workflow_role_binding_promotion',
                            'workflow_role_binding_candidate_snapshot',
                            'workflow_role_binding_snapshot_event'))
  + (SELECT COUNT(*) FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table IN ('workflow_role_binding_promotion',
                                    'workflow_role_binding_candidate_snapshot',
                                    'workflow_role_binding_snapshot_event'))
  + (SELECT COUNT(*) FROM role_runtime_activation_request
       WHERE status = 'PERSISTED'
         AND (resolver_code IS NULL OR resolver_code = ''
           OR resolver_version IS NULL OR resolver_version = ''
           OR NOT REGEXP_LIKE(resolver_code, '^[A-Z0-9_]+$', 'c')
           OR NOT REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')
           OR contract_hash NOT REGEXP '^[0-9a-f]{64}$'
           OR binding_hash NOT REGEXP '^[0-9a-f]{64}$'
           OR candidate_hash NOT REGEXP '^[0-9a-f]{64}$'
           OR directory_contract_hash NOT REGEXP '^[0-9a-f]{64}$'
           OR activation_hash NOT REGEXP '^[0-9a-f]{64}$'
           OR approval_evidence_hash NOT REGEXP '^[0-9a-f]{64}$'))
  + (SELECT COUNT(*) FROM role_runtime_activation_approval a
       LEFT JOIN role_runtime_activation_request r
         ON r.activation_id = a.activation_id AND r.delete_token = a.delete_token
      WHERE r.id IS NULL OR a.activation_hash <> r.activation_hash
         OR a.contract_hash <> r.contract_hash OR a.binding_hash <> r.binding_hash)
  + (SELECT COUNT(*) FROM role_runtime_activation_evidence e
       LEFT JOIN role_runtime_activation_approval a
         ON a.id = e.approval_id AND a.activation_id = e.activation_id
        AND a.delete_token = e.delete_token
      WHERE a.id IS NULL OR e.activation_hash <> a.activation_hash
         OR e.contract_hash <> a.contract_hash OR e.binding_hash <> a.binding_hash);

DROP TEMPORARY TABLE tmp_role_binding_v2614_guard;

CREATE TABLE workflow_role_binding_promotion (
    id BIGINT NOT NULL,
    promotion_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_delete_token BIGINT NOT NULL DEFAULT 0,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_audit_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision BIGINT NOT NULL,
    business_scope VARCHAR(200) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NOT NULL,
    permission_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promoted_at DATETIME(3) NOT NULL,
    promoted_by VARCHAR(100) NOT NULL,
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
    UNIQUE KEY uk_role_binding_promotion_owner (id, promotion_id, activation_id, delete_token),
    UNIQUE KEY uk_role_binding_promotion_id (promotion_id, delete_token),
    UNIQUE KEY uk_role_binding_promotion_hash (promotion_hash, delete_token),
    KEY idx_role_binding_promotion_activation (activation_id, activation_delete_token),
    KEY idx_role_binding_promotion_resolver (resolver_code, resolver_version, status),
    CONSTRAINT chk_role_binding_promotion_identity CHECK (
        REGEXP_LIKE(resolver_code, '^[A-Z0-9_]+$', 'c')
        AND REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')),
    CONSTRAINT chk_role_binding_promotion_hashes CHECK (
        activation_hash REGEXP '^[0-9a-f]{64}$'
        AND activation_audit_hash REGEXP '^[0-9a-f]{64}$'
        AND approval_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND resolver_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'
        AND candidate_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND permission_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND promotion_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND promotion_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_binding_promotion_status CHECK (status = 'PROMOTED'),
    CONSTRAINT chk_role_binding_promotion_window CHECK (effective_until > effective_from),
    CONSTRAINT chk_role_binding_promotion_revision CHECK (directory_revision >= 0),
    CONSTRAINT chk_role_binding_promotion_canonical CHECK (
        canonical_version = 'ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1'),
    CONSTRAINT chk_role_binding_promotion_append_only CHECK (
        deleted = 0 AND delete_token = 0 AND activation_delete_token = 0 AND version = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only approved ROLE binding promotion fact; never executable';

CREATE TABLE workflow_role_binding_candidate_snapshot (
    id BIGINT NOT NULL,
    snapshot_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_row_id BIGINT NOT NULL,
    promotion_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_delete_token BIGINT NOT NULL DEFAULT 0,
    activation_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_reference_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision BIGINT NOT NULL,
    business_scope VARCHAR(200) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NOT NULL,
    activation_audit_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_set_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    UNIQUE KEY uk_role_binding_snapshot_owner (
        id, snapshot_id, promotion_id, activation_id, delete_token),
    UNIQUE KEY uk_role_binding_snapshot_id (snapshot_id, delete_token),
    UNIQUE KEY uk_role_binding_snapshot_promotion (promotion_id, delete_token),
    UNIQUE KEY uk_role_binding_snapshot_hash (snapshot_hash, delete_token),
    KEY idx_role_binding_snapshot_promotion_fk (
        promotion_row_id, promotion_id, activation_id, delete_token),
    KEY idx_role_binding_snapshot_activation (activation_id, activation_delete_token),
    CONSTRAINT chk_role_binding_snapshot_identity CHECK (
        REGEXP_LIKE(resolver_code, '^[A-Z0-9_]+$', 'c')
        AND REGEXP_LIKE(resolver_version, '^[A-Z0-9_]+$', 'c')),
    CONSTRAINT chk_role_binding_snapshot_hashes CHECK (
        activation_hash REGEXP '^[0-9a-f]{64}$'
        AND promotion_hash REGEXP '^[0-9a-f]{64}$'
        AND promotion_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND promotion_reference_hash REGEXP '^[0-9a-f]{64}$'
        AND resolver_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'
        AND candidate_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND activation_audit_hash REGEXP '^[0-9a-f]{64}$'
        AND approval_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND permission_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND evidence_set_hash REGEXP '^[0-9a-f]{64}$'
        AND snapshot_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_role_binding_snapshot_window CHECK (effective_until > effective_from),
    CONSTRAINT chk_role_binding_snapshot_revision CHECK (directory_revision >= 0),
    CONSTRAINT chk_role_binding_snapshot_canonical CHECK (
        canonical_version = 'ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1'),
    CONSTRAINT chk_role_binding_snapshot_append_only CHECK (
        deleted = 0 AND delete_token = 0 AND activation_delete_token = 0 AND version = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable pre-instance ROLE runtime binding candidate snapshot';

CREATE TABLE workflow_role_binding_snapshot_event (
    id BIGINT NOT NULL,
    snapshot_row_id BIGINT NOT NULL,
    snapshot_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    promotion_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence_no BIGINT NOT NULL,
    from_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    operator_id VARCHAR(100) NOT NULL,
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
    UNIQUE KEY uk_role_binding_event_sequence (snapshot_id, sequence_no, delete_token),
    UNIQUE KEY uk_role_binding_event_hash (event_hash, delete_token),
    KEY idx_role_binding_event_owner (
        snapshot_row_id, snapshot_id, promotion_id, activation_id, delete_token),
    CONSTRAINT chk_role_binding_event_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_role_binding_event_status CHECK (
        to_status IN ('CREATED','VALIDATED','ACTIVE','BLOCKED','REVOKED')
        AND (from_status IS NULL OR from_status IN
             ('CREATED','VALIDATED','ACTIVE','BLOCKED','REVOKED'))),
    CONSTRAINT chk_role_binding_event_hashes CHECK (
        source_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND event_hash REGEXP '^[0-9a-f]{64}$'
        AND (previous_event_hash IS NULL OR previous_event_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT chk_role_binding_event_canonical CHECK (
        canonical_version = 'ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1'),
    CONSTRAINT chk_role_binding_event_append_only CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only ROLE binding snapshot lifecycle hash chain';

ALTER TABLE workflow_role_binding_promotion
    ADD CONSTRAINT fk_role_binding_promotion_activation
    FOREIGN KEY (activation_id, activation_delete_token)
    REFERENCES role_runtime_activation_request(activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_binding_candidate_snapshot
    ADD CONSTRAINT fk_role_binding_snapshot_promotion
    FOREIGN KEY (promotion_row_id, promotion_id, activation_id, delete_token)
    REFERENCES workflow_role_binding_promotion(id, promotion_id, activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_role_binding_snapshot_activation
    FOREIGN KEY (activation_id, activation_delete_token)
    REFERENCES role_runtime_activation_request(activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE workflow_role_binding_snapshot_event
    ADD CONSTRAINT fk_role_binding_event_snapshot
    FOREIGN KEY (snapshot_row_id, snapshot_id, promotion_id, activation_id, delete_token)
    REFERENCES workflow_role_binding_candidate_snapshot(
        id, snapshot_id, promotion_id, activation_id, delete_token)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

DELIMITER $$

CREATE TRIGGER trg_role_binding_promotion_insert_guard
BEFORE INSERT ON workflow_role_binding_promotion
FOR EACH ROW
BEGIN
    DECLARE matched_request BIGINT DEFAULT 0;
    DECLARE approval_count BIGINT DEFAULT 0;
    DECLARE evidence_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO matched_request
      FROM role_runtime_activation_request r
     WHERE r.activation_id = NEW.activation_id
       AND r.delete_token = NEW.activation_delete_token
       AND r.status = 'PERSISTED'
       AND r.activation_hash = NEW.activation_hash
       AND r.resolver_code = NEW.resolver_code
       AND r.resolver_version = NEW.resolver_version
       AND r.contract_hash = NEW.resolver_contract_hash
       AND r.binding_hash = NEW.binding_hash
       AND r.candidate_hash = NEW.candidate_hash
       AND r.directory_contract_hash = NEW.directory_contract_hash
       AND r.directory_revision = NEW.directory_revision
       AND r.business_scope = NEW.business_scope
       AND r.effective_at = NEW.effective_from
       AND r.approval_evidence_hash = NEW.approval_evidence_hash;
    SELECT COUNT(*) INTO approval_count
      FROM role_runtime_activation_approval a
     WHERE a.activation_id = NEW.activation_id AND a.delete_token = 0
       AND a.decision = 'APPROVE' AND a.activation_hash = NEW.activation_hash
       AND a.contract_hash = NEW.resolver_contract_hash AND a.binding_hash = NEW.binding_hash;
    SELECT COUNT(DISTINCT e.evidence_type) INTO evidence_count
      FROM role_runtime_activation_evidence e
     WHERE e.activation_id = NEW.activation_id AND e.delete_token = 0
       AND e.activation_hash = NEW.activation_hash
       AND e.contract_hash = NEW.resolver_contract_hash AND e.binding_hash = NEW.binding_hash;
    IF matched_request <> 1 OR approval_count <> 3 OR evidence_count <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_BINDING_PROMOTION_EVIDENCE_INVALID';
    END IF;
END$$

CREATE TRIGGER trg_role_binding_snapshot_insert_guard
BEFORE INSERT ON workflow_role_binding_candidate_snapshot
FOR EACH ROW
BEGIN
    DECLARE matched_promotion BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO matched_promotion
      FROM workflow_role_binding_promotion p
     WHERE p.id = NEW.promotion_row_id AND p.promotion_id = NEW.promotion_id
       AND p.activation_id = NEW.activation_id AND p.delete_token = NEW.delete_token
       AND p.status = 'PROMOTED' AND p.activation_hash = NEW.activation_hash
       AND p.promotion_hash = NEW.promotion_hash
       AND p.promotion_evidence_hash = NEW.promotion_evidence_hash
       AND p.resolver_code = NEW.resolver_code
       AND p.resolver_version = NEW.resolver_version
       AND p.resolver_contract_hash = NEW.resolver_contract_hash
       AND p.binding_hash = NEW.binding_hash AND p.candidate_hash = NEW.candidate_hash
       AND p.directory_contract_hash = NEW.directory_contract_hash
       AND p.directory_revision = NEW.directory_revision
       AND p.business_scope = NEW.business_scope
       AND p.effective_from = NEW.effective_from
       AND p.effective_until = NEW.effective_until
       AND p.activation_audit_hash = NEW.activation_audit_hash
       AND p.approval_evidence_hash = NEW.approval_evidence_hash
       AND p.permission_evidence_hash = NEW.permission_evidence_hash;
    IF matched_promotion <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_BINDING_SNAPSHOT_PROMOTION_MISMATCH';
    END IF;
END$$

CREATE TRIGGER trg_role_binding_event_insert_guard
BEFORE INSERT ON workflow_role_binding_snapshot_event
FOR EACH ROW
BEGIN
    DECLARE parent_count BIGINT DEFAULT 0;
    DECLARE prior_count BIGINT DEFAULT 0;
    DECLARE prior_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE prior_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    SELECT COUNT(*) INTO parent_count
      FROM workflow_role_binding_candidate_snapshot s
     WHERE s.id = NEW.snapshot_row_id AND s.snapshot_id = NEW.snapshot_id
       AND s.promotion_id = NEW.promotion_id AND s.activation_id = NEW.activation_id
       AND s.delete_token = NEW.delete_token;
    IF parent_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_BINDING_EVENT_OWNER_INVALID';
    END IF;
    IF NEW.sequence_no = 1 THEN
        IF NEW.from_status IS NOT NULL OR NEW.to_status <> 'CREATED'
           OR NEW.previous_event_hash IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_BINDING_EVENT_INITIAL_STATE_INVALID';
        END IF;
    ELSE
        SELECT COUNT(*), MAX(to_status), MAX(event_hash)
          INTO prior_count, prior_status, prior_hash
          FROM workflow_role_binding_snapshot_event
         WHERE snapshot_id = NEW.snapshot_id AND sequence_no = NEW.sequence_no - 1
           AND delete_token = NEW.delete_token;
        IF prior_count <> 1 OR prior_status <> NEW.from_status
           OR prior_hash <> NEW.previous_event_hash
           OR NOT ((NEW.from_status = 'CREATED' AND NEW.to_status IN ('VALIDATED','BLOCKED'))
                OR (NEW.from_status = 'VALIDATED' AND NEW.to_status IN ('ACTIVE','BLOCKED'))
                OR (NEW.from_status = 'ACTIVE' AND NEW.to_status = 'REVOKED')) THEN
            SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_BINDING_EVENT_CHAIN_INVALID';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_role_binding_promotion_no_update
BEFORE UPDATE ON workflow_role_binding_promotion
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_PROMOTION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_binding_promotion_no_delete
BEFORE DELETE ON workflow_role_binding_promotion
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_PROMOTION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_binding_snapshot_no_update
BEFORE UPDATE ON workflow_role_binding_candidate_snapshot
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_SNAPSHOT_IMMUTABLE'; END$$
CREATE TRIGGER trg_role_binding_snapshot_no_delete
BEFORE DELETE ON workflow_role_binding_candidate_snapshot
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_SNAPSHOT_IMMUTABLE'; END$$
CREATE TRIGGER trg_role_binding_event_no_update
BEFORE UPDATE ON workflow_role_binding_snapshot_event
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_EVENT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_binding_event_no_delete
BEFORE DELETE ON workflow_role_binding_snapshot_event
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
    MESSAGE_TEXT = 'ROLE_BINDING_EVENT_APPEND_ONLY'; END$$

DELIMITER ;

