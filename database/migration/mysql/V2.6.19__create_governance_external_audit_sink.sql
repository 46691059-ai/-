-- Independent Governance External Audit Sink persistence and immutable Workflow payload envelope.
-- ROLE Runtime remains disabled; this migration does not activate any resolver or runtime path.

-- Extend the immutable V2.6.18 governance control domain with the remaining
-- versioned Platform SoD rules. This is an additive forward change; V2.6.18 is untouched.
ALTER TABLE workflow_role_runtime_governance_control
    DROP CHECK ck_role_runtime_control_type,
    ADD CONSTRAINT ck_role_runtime_control_type CHECK
      (control_type IN ('FEATURE_FLAG','CANARY','KILL_SWITCH','BUSINESS_SOD','PLATFORM_SOD'));

-- V2.6.18 intentionally made the outbox strict, but omitted the governed replay
-- transition used by the dispatcher. Preserve every immutable-column/CAS rule and
-- admit only DEAD -> RETRY when the explicit replay marker is written.
DROP TRIGGER trg_role_external_outbox_update;
DELIMITER $$
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
      OR (OLD.status='DEAD' AND NEW.status='RETRY' AND NEW.last_error_code='CONTROLLED_REPLAY')
      OR (OLD.status=NEW.status)) THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_OUTBOX_TRANSITION_INVALID';
  END IF;
END$$
DELIMITER ;

ALTER TABLE workflow_task_claim
    ADD COLUMN admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER eligibility_contract_version,
    ADD KEY idx_workflow_task_claim_admission (admission_id, delete_token),
    ADD CONSTRAINT fk_workflow_task_claim_admission FOREIGN KEY (admission_id, delete_token)
      REFERENCES workflow_role_runtime_execution_admission(admission_id, delete_token),
    ADD CONSTRAINT ck_workflow_task_claim_admission_binding CHECK
      ((eligibility_evidence_id IS NULL AND admission_id IS NULL)
       OR (eligibility_evidence_id IS NOT NULL AND admission_id IS NOT NULL));

CREATE TABLE governance_external_audit_event (
    audit_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workflow_instance_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_execution_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_user_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    runtime_binding_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admission_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eligibility_evidence_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    directory_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_pool_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    eligibility_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_audit_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correlation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_payload JSON NOT NULL,
    received_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_event_id),
    UNIQUE KEY uk_governance_audit_payload (payload_hash),
    KEY idx_governance_audit_claim (claim_id),
    KEY idx_governance_audit_received (received_at),
    CONSTRAINT ck_governance_audit_hashes CHECK
      (directory_result_hash REGEXP '^[0-9a-f]{64}$'
       AND candidate_pool_hash REGEXP '^[0-9a-f]{64}$'
       AND eligibility_hash REGEXP '^[0-9a-f]{64}$'
       AND claim_audit_hash REGEXP '^[0-9a-f]{64}$'
       AND payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_governance_audit_payload_identity CHECK
      (JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.auditEventId')) = audit_event_id
       AND JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.payloadHash')) = payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE governance_external_audit_receipt (
    external_receipt_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    audit_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    received_at DATETIME(3) NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (external_receipt_id),
    UNIQUE KEY uk_governance_receipt_event (audit_event_id),
    UNIQUE KEY uk_governance_receipt_hash (receipt_hash),
    CONSTRAINT fk_governance_receipt_event FOREIGN KEY (audit_event_id)
      REFERENCES governance_external_audit_event(audit_event_id),
    CONSTRAINT ck_governance_receipt_status CHECK (receipt_status IN ('ACCEPTED','REJECTED')),
    CONSTRAINT ck_governance_receipt_hashes CHECK
      (payload_hash REGEXP '^[0-9a-f]{64}$' AND receipt_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_role_external_audit_payload (
    outbox_id BIGINT NOT NULL,
    audit_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_payload JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_role_external_payload_event (audit_event_id),
    CONSTRAINT fk_role_external_payload_outbox FOREIGN KEY (outbox_id)
      REFERENCES workflow_role_external_audit_outbox(id),
    CONSTRAINT ck_role_external_payload_hash CHECK (payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_role_external_payload_identity CHECK
      (JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.auditEventId')) = audit_event_id
       AND JSON_UNQUOTE(JSON_EXTRACT(event_payload,'$.payloadHash')) = payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_governance_audit_event_update BEFORE UPDATE ON governance_external_audit_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='GOVERNANCE_AUDIT_EVENT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_governance_audit_event_delete BEFORE DELETE ON governance_external_audit_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='GOVERNANCE_AUDIT_EVENT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_governance_audit_receipt_update BEFORE UPDATE ON governance_external_audit_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='GOVERNANCE_AUDIT_RECEIPT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_governance_audit_receipt_delete BEFORE DELETE ON governance_external_audit_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='GOVERNANCE_AUDIT_RECEIPT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_external_payload_update BEFORE UPDATE ON workflow_role_external_audit_payload FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_PAYLOAD_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_external_payload_delete BEFORE DELETE ON workflow_role_external_audit_payload FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ROLE_EXTERNAL_AUDIT_PAYLOAD_APPEND_ONLY'; END$$
DELIMITER ;
