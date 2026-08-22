-- Durable, PII-minimized Approval Role Directory provider audit ledger.
-- This asset does not activate ROLE_DIRECTORY_V1, ROLE Runtime, or Canary.

CREATE TABLE approval_role_directory_provider_audit (
    audit_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    audit_event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correlation_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_identity VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    environment_identity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    caller_service_identity VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    enterprise_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    organization_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    effective_at DATETIME(3) NULL,
    contract_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_revision BIGINT NULL,
    directory_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    candidate_count INT NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    retention_policy VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_YET_PRODUCTION_APPROVED',
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_approval_role_provider_audit_event (audit_event_id),
    UNIQUE KEY uk_approval_role_provider_audit_evidence (evidence_hash),
    KEY idx_approval_role_provider_audit_request (request_id, completed_at),
    KEY idx_approval_role_provider_audit_correlation (correlation_id, completed_at),
    KEY idx_approval_role_provider_audit_directory (enterprise_id, organization_id, role_code, effective_at),
    KEY idx_approval_role_provider_audit_outcome (outcome, completed_at),
    CONSTRAINT ck_approval_role_provider_audit_identity CHECK
      (provider_code REGEXP '^[A-Z][A-Z0-9_]{2,99}$'
       AND CHAR_LENGTH(TRIM(provider_version)) > 0
       AND CHAR_LENGTH(TRIM(service_identity)) > 0
       AND environment_identity IN ('TEST','PREPROD','PRODUCTION')
       AND CHAR_LENGTH(TRIM(request_id)) > 0),
    CONSTRAINT ck_approval_role_provider_audit_hashes CHECK
      (contract_hash REGEXP '^[0-9a-f]{64}$'
       AND request_hash REGEXP '^[0-9a-f]{64}$'
       AND evidence_hash REGEXP '^[0-9a-f]{64}$'
       AND (directory_result_hash IS NULL OR directory_result_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_approval_role_provider_audit_outcome CHECK
      (outcome IN ('SUCCESS','REJECTED','FAILED')),
    CONSTRAINT ck_approval_role_provider_audit_result CHECK
      ((outcome='SUCCESS' AND failure_code IS NULL AND directory_revision IS NOT NULL
        AND directory_revision >= 0 AND directory_result_hash IS NOT NULL
        AND candidate_count IS NOT NULL AND candidate_count >= 0
        AND enterprise_id IS NOT NULL AND organization_id IS NOT NULL
        AND role_code IS NOT NULL AND effective_at IS NOT NULL)
       OR
       (outcome IN ('REJECTED','FAILED') AND failure_code IS NOT NULL
        AND CHAR_LENGTH(TRIM(failure_code)) > 0
        AND (directory_revision IS NULL OR directory_revision >= 0)
        AND (candidate_count IS NULL OR candidate_count >= 0))),
    CONSTRAINT ck_approval_role_provider_audit_time CHECK
      (completed_at >= started_at),
    CONSTRAINT ck_approval_role_provider_audit_retention CHECK
      (retention_policy='NOT_YET_PRODUCTION_APPROVED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_approval_role_provider_audit_update
BEFORE UPDATE ON approval_role_directory_provider_audit FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,
    MESSAGE_TEXT='APPROVAL_ROLE_DIRECTORY_PROVIDER_AUDIT_APPEND_ONLY';
END$$
CREATE TRIGGER trg_approval_role_provider_audit_delete
BEFORE DELETE ON approval_role_directory_provider_audit FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,
    MESSAGE_TEXT='APPROVAL_ROLE_DIRECTORY_PROVIDER_AUDIT_APPEND_ONLY';
END$$
DELIMITER ;
