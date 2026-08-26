-- Exact six-dimensional Canary authorization ledger. ROLE Runtime remains disabled.
CREATE TABLE workflow_role_canary_scope_governance (
    id BIGINT NOT NULL,
    previous_record_id BIGINT NULL,
    enterprise_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    governance_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    governance_revision BIGINT NOT NULL,
    directory_revision VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_candidate_count INT NOT NULL,
    directory_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    release_tag VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    release_commit CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    structural_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_actor VARCHAR(100) NULL,
    approved_at DATETIME(3) NULL,
    enablement_actor VARCHAR(100) NULL,
    enabled_at DATETIME(3) NULL,
    suspended_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    reason VARCHAR(500) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(100) NOT NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_canary_scope_revision
        (enterprise_id, organization_id, definition_id, definition_version_id,
         node_id, role_code, governance_revision, delete_token),
    UNIQUE KEY uk_canary_scope_previous (previous_record_id, delete_token),
    KEY idx_canary_scope_latest
        (enterprise_id, organization_id, definition_id, definition_version_id,
         node_id, role_code, deleted, governance_revision),
    CONSTRAINT fk_canary_scope_previous FOREIGN KEY (previous_record_id)
        REFERENCES workflow_role_canary_scope_governance(id),
    CONSTRAINT ck_canary_scope_identity CHECK
        (enterprise_id > 0 AND organization_id > 0 AND definition_id > 0
         AND definition_version_id > 0 AND node_id > 0
         AND CHAR_LENGTH(TRIM(role_code)) > 0),
    CONSTRAINT ck_canary_scope_state CHECK
        (governance_state IN ('PROPOSED','APPROVED_NOT_ENABLED','ENABLED','SUSPENDED','REVOKED')),
    CONSTRAINT ck_canary_scope_revision CHECK (governance_revision > 0),
    CONSTRAINT ck_canary_scope_directory CHECK
        (CHAR_LENGTH(TRIM(directory_revision)) > 0 AND directory_candidate_count > 0),
    CONSTRAINT ck_canary_scope_hashes CHECK
        (directory_result_hash REGEXP '^[0-9a-f]{64}$'
         AND version_binding_hash REGEXP '^[0-9a-f]{64}$'
         AND manifest_hash REGEXP '^[0-9a-f]{64}$'
         AND content_hash REGEXP '^[0-9a-f]{64}$'
         AND structural_fingerprint REGEXP '^[0-9a-f]{64}$'
         AND release_commit REGEXP '^[0-9a-f]{40}$'),
    CONSTRAINT ck_canary_scope_window CHECK
        (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_canary_scope_audit_state CHECK
        ((governance_state='PROPOSED' AND approved_at IS NULL AND enabled_at IS NULL
          AND suspended_at IS NULL AND revoked_at IS NULL)
         OR (governance_state='APPROVED_NOT_ENABLED' AND approval_actor IS NOT NULL
          AND approved_at IS NOT NULL AND enabled_at IS NULL AND revoked_at IS NULL)
         OR (governance_state='ENABLED' AND approval_actor IS NOT NULL
          AND approved_at IS NOT NULL AND enablement_actor IS NOT NULL
          AND enabled_at IS NOT NULL AND revoked_at IS NULL)
         OR (governance_state='SUSPENDED' AND approval_actor IS NOT NULL
          AND approved_at IS NOT NULL AND suspended_at IS NOT NULL AND revoked_at IS NULL)
         OR (governance_state='REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT ck_canary_scope_immutable CHECK
        (deleted=0 AND delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_canary_scope_insert_guard
BEFORE INSERT ON workflow_role_canary_scope_governance FOR EACH ROW
BEGIN
  DECLARE parent_state VARCHAR(32);
  DECLARE parent_revision BIGINT;
  DECLARE parent_scope_count INT DEFAULT 0;
  DECLARE parent_evidence_count INT DEFAULT 0;
  IF NEW.previous_record_id IS NULL THEN
    IF NEW.governance_revision<>1 OR NEW.governance_state<>'PROPOSED' THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_ROOT_MUST_BE_PROPOSED';
    END IF;
  ELSE
    SELECT governance_state,governance_revision,
           (enterprise_id=NEW.enterprise_id AND organization_id=NEW.organization_id
            AND definition_id=NEW.definition_id AND definition_version_id=NEW.definition_version_id
            AND node_id=NEW.node_id AND BINARY role_code=BINARY NEW.role_code),
           (BINARY directory_revision=BINARY NEW.directory_revision
            AND directory_candidate_count=NEW.directory_candidate_count
            AND BINARY directory_result_hash=BINARY NEW.directory_result_hash
            AND BINARY version_binding_hash=BINARY NEW.version_binding_hash
            AND BINARY manifest_hash=BINARY NEW.manifest_hash
            AND BINARY content_hash=BINARY NEW.content_hash
            AND BINARY release_tag=BINARY NEW.release_tag
            AND BINARY release_commit=BINARY NEW.release_commit
            AND BINARY structural_fingerprint=BINARY NEW.structural_fingerprint)
      INTO parent_state,parent_revision,parent_scope_count,parent_evidence_count
      FROM workflow_role_canary_scope_governance WHERE id=NEW.previous_record_id;
    IF parent_scope_count<>1 OR NEW.governance_revision<>parent_revision+1 THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_REVISION_OR_IDENTITY_MISMATCH';
    END IF;
    IF parent_evidence_count<>1 THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_APPROVAL_EVIDENCE_DRIFT';
    END IF;
    IF NOT ((parent_state='PROPOSED' AND NEW.governance_state IN ('APPROVED_NOT_ENABLED','REVOKED'))
      OR (parent_state='APPROVED_NOT_ENABLED' AND NEW.governance_state IN ('ENABLED','REVOKED'))
      OR (parent_state='ENABLED' AND NEW.governance_state IN ('SUSPENDED','REVOKED'))
      OR (parent_state='SUSPENDED' AND NEW.governance_state IN ('ENABLED','REVOKED'))) THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_TRANSITION_INVALID';
    END IF;
  END IF;
END$$
CREATE TRIGGER trg_canary_scope_update
BEFORE UPDATE ON workflow_role_canary_scope_governance FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_APPEND_ONLY'; END$$
CREATE TRIGGER trg_canary_scope_delete
BEFORE DELETE ON workflow_role_canary_scope_governance FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='CANARY_SCOPE_APPEND_ONLY'; END$$
DELIMITER ;
