-- Independent ROLE Runtime activation event. No Canary, traffic, task, claim or Kill Switch mutation.
CREATE TABLE workflow_role_runtime_activation_event (
    id BIGINT NOT NULL,
    event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enterprise_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    previous_state VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resulting_state VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_commit CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observation_evidence_commit CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    runtime_enablement_evidence_commit CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    runtime_release_commit CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    runtime_release_tag VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    directory_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    structural_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_runtime_activation_event_id (event_id, delete_token),
    UNIQUE KEY uk_role_runtime_activation_scope_type
        (enterprise_id, organization_id, definition_id, definition_version_id,
         node_id, role_code, event_type, delete_token),
    UNIQUE KEY uk_role_runtime_activation_scope_sequence
        (enterprise_id, organization_id, definition_id, definition_version_id,
         node_id, role_code, sequence, delete_token),
    UNIQUE KEY uk_role_runtime_activation_scope_revision
        (enterprise_id, organization_id, definition_id, definition_version_id,
         node_id, role_code, revision, delete_token),
    KEY idx_role_runtime_activation_authorization (authorization_id, authorization_type),
    CONSTRAINT fk_role_runtime_activation_authorization
        FOREIGN KEY (authorization_id, delete_token)
        REFERENCES role_runtime_activation_request(activation_id, delete_token)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_role_runtime_activation_scope CHECK
        (enterprise_id>0 AND organization_id>0 AND definition_id>0
         AND definition_version_id>0 AND node_id>0 AND CHAR_LENGTH(TRIM(role_code))>0),
    CONSTRAINT ck_role_runtime_activation_transition CHECK
        (event_type='ROLE_RUNTIME_ACTIVATED' AND previous_state='DISABLED'
         AND resulting_state='ACTIVATED'),
    CONSTRAINT ck_role_runtime_activation_authorization_type CHECK
        (authorization_type='ACTIVATE_ROLE_RUNTIME'),
    CONSTRAINT ck_role_runtime_activation_order CHECK (sequence=1 AND revision=1),
    CONSTRAINT ck_role_runtime_activation_commits CHECK
        (authorization_commit REGEXP '^[0-9a-f]{40}$'
         AND observation_evidence_commit REGEXP '^[0-9a-f]{40}$'
         AND runtime_enablement_evidence_commit REGEXP '^[0-9a-f]{40}$'
         AND runtime_release_commit REGEXP '^[0-9a-f]{40}$'),
    CONSTRAINT ck_role_runtime_activation_hashes CHECK
        (directory_result_hash REGEXP '^[0-9a-f]{64}$'
         AND version_binding_hash REGEXP '^[0-9a-f]{64}$'
         AND manifest_hash REGEXP '^[0-9a-f]{64}$'
         AND content_hash REGEXP '^[0-9a-f]{64}$'
         AND structural_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_role_runtime_activation_immutable CHECK
        (deleted=0 AND delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Authoritative append-only independent ROLE Runtime activation event';

DELIMITER $$
CREATE TRIGGER trg_role_runtime_activation_event_insert_guard
BEFORE INSERT ON workflow_role_runtime_activation_event FOR EACH ROW
BEGIN
    DECLARE request_count INT DEFAULT 0;
    DECLARE approval_count INT DEFAULT 0;
    DECLARE evidence_count INT DEFAULT 0;
    SELECT COUNT(*) INTO request_count FROM role_runtime_activation_request
     WHERE activation_id=NEW.authorization_id AND delete_token=0 AND status='PERSISTED';
    SELECT COUNT(DISTINCT approver_type) INTO approval_count FROM role_runtime_activation_approval
     WHERE activation_id=NEW.authorization_id AND delete_token=0 AND decision='APPROVE'
       AND approver_type IN ('BUSINESS_OWNER','SECURITY_AUDIT','RELEASE_APPROVER');
    SELECT COUNT(*) INTO evidence_count FROM role_runtime_activation_evidence
     WHERE activation_id=NEW.authorization_id AND delete_token=0;
    IF request_count<>1 OR approval_count<>3 OR evidence_count<1 THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,
        MESSAGE_TEXT='ROLE_RUNTIME_ACTIVATION_AUTHORIZATION_INCOMPLETE';
    END IF;
END$$
CREATE TRIGGER trg_role_runtime_activation_event_no_update
BEFORE UPDATE ON workflow_role_runtime_activation_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,
  MESSAGE_TEXT='ROLE_RUNTIME_ACTIVATION_EVENT_APPEND_ONLY'; END$$
CREATE TRIGGER trg_role_runtime_activation_event_no_delete
BEFORE DELETE ON workflow_role_runtime_activation_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,
  MESSAGE_TEXT='ROLE_RUNTIME_ACTIVATION_EVENT_APPEND_ONLY'; END$$
DELIMITER ;
