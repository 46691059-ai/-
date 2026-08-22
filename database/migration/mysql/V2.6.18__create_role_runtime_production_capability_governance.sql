-- ROLE Runtime production capability governance only. Runtime remains disabled by default.
CREATE TABLE workflow_role_runtime_governance_control (
    id BIGINT NOT NULL,
    control_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_key VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    definition_id BIGINT NULL,
    definition_version_id BIGINT NULL,
    node_id BIGINT NULL,
    business_object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    business_object_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    business_rule_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    decision VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_version BIGINT NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    policy_payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_runtime_control_version
        (control_type, scope_key, config_version, delete_token),
    KEY idx_role_runtime_control_lookup
        (control_type, scope_key, effective_from, effective_to, deleted, config_version),
    CONSTRAINT ck_role_runtime_control_type CHECK
        (control_type IN ('FEATURE_FLAG','CANARY','KILL_SWITCH','BUSINESS_SOD')),
    CONSTRAINT ck_role_runtime_control_scope CHECK
        (scope_type IN ('GLOBAL','ENTERPRISE','WORKFLOW_DEFINITION','DEFINITION_VERSION','NODE','BUSINESS_OBJECT')),
    CONSTRAINT ck_role_runtime_control_decision CHECK
        (decision IN ('ON','OFF','ALLOW','DENY','INDETERMINATE','STOP_NEW_ONLY','STOP_NEW_AND_CLAIM','FREEZE_ALL_PENDING')),
    CONSTRAINT ck_role_runtime_control_version CHECK (config_version > 0 AND version = 0),
    CONSTRAINT ck_role_runtime_control_window CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_role_runtime_control_hashes CHECK
        (policy_payload_hash REGEXP '^[0-9a-f]{64}$' AND evidence_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_role_runtime_control_delete CHECK
        ((deleted = 0 AND delete_token = 0) OR (deleted = 1 AND delete_token > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_role_external_audit_outbox (
    id BIGINT NOT NULL,
    audit_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_id BIGINT NOT NULL,
    claim_audit_id BIGINT NOT NULL,
    provider_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_time DATETIME(3) NULL,
    last_error_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_external_audit_event (audit_event_id, delete_token),
    UNIQUE KEY uk_role_external_audit_claim (claim_id, delete_token),
    KEY idx_role_external_audit_dispatch (status, next_attempt_time, attempt_count),
    CONSTRAINT fk_role_external_audit_claim FOREIGN KEY (claim_id)
        REFERENCES workflow_task_claim(id),
    CONSTRAINT fk_role_external_audit_claim_audit FOREIGN KEY (claim_audit_id)
        REFERENCES workflow_task_claim_audit(id),
    CONSTRAINT ck_role_external_audit_status CHECK
        (status IN ('PENDING','SENDING','RETRY','ACKNOWLEDGED','DEAD')),
    CONSTRAINT ck_role_external_audit_hash CHECK (payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_role_external_audit_attempt CHECK (attempt_count BETWEEN 0 AND 20),
    CONSTRAINT ck_role_external_audit_delete CHECK
        ((deleted = 0 AND delete_token = 0) OR (deleted = 1 AND delete_token > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_role_external_audit_receipt (
    id BIGINT NOT NULL,
    outbox_id BIGINT NOT NULL,
    audit_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_receipt_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    received_at DATETIME(3) NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_external_audit_receipt_outbox (outbox_id, delete_token),
    UNIQUE KEY uk_role_external_audit_receipt_external (external_receipt_id, delete_token),
    CONSTRAINT fk_role_external_audit_receipt_outbox FOREIGN KEY (outbox_id)
        REFERENCES workflow_role_external_audit_outbox(id),
    CONSTRAINT ck_role_external_audit_receipt_status CHECK (status IN ('ACCEPTED','REJECTED','UNKNOWN')),
    CONSTRAINT ck_role_external_audit_receipt_hashes CHECK
        (payload_hash REGEXP '^[0-9a-f]{64}$' AND receipt_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_role_external_audit_receipt_immutable CHECK (version = 0 AND deleted = 0 AND delete_token = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_role_runtime_control_update
BEFORE UPDATE ON workflow_role_runtime_governance_control FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_RUNTIME_CONTROL_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_runtime_control_delete
BEFORE DELETE ON workflow_role_runtime_governance_control FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_RUNTIME_CONTROL_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_external_receipt_update
BEFORE UPDATE ON workflow_role_external_audit_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_RECEIPT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_external_receipt_delete
BEFORE DELETE ON workflow_role_external_audit_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_RECEIPT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_external_outbox_delete
BEFORE DELETE ON workflow_role_external_audit_outbox FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_OUTBOX_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_role_external_outbox_update
BEFORE UPDATE ON workflow_role_external_audit_outbox FOR EACH ROW
BEGIN
  IF NEW.id<>OLD.id OR NEW.audit_event_id<>OLD.audit_event_id OR NEW.claim_id<>OLD.claim_id
     OR NEW.claim_audit_id<>OLD.claim_audit_id OR NEW.payload_hash<>OLD.payload_hash
     OR NEW.provider_code<>OLD.provider_code OR NEW.provider_version<>OLD.provider_version
     OR NEW.deleted<>OLD.deleted OR NEW.delete_token<>OLD.delete_token
     OR NEW.version<>OLD.version+1 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_OUTBOX_CAS_ONLY';
  END IF;
  IF NOT ((OLD.status='PENDING' AND NEW.status IN ('SENDING','RETRY','DEAD'))
      OR (OLD.status='RETRY' AND NEW.status IN ('SENDING','RETRY','DEAD'))
      OR (OLD.status='SENDING' AND NEW.status IN ('ACKNOWLEDGED','RETRY','DEAD'))
      OR (OLD.status=NEW.status)) THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_OUTBOX_TRANSITION_INVALID';
  END IF;
END$$
DELIMITER ;
