-- Sprint 2-3.7-WF5.7.2: forward-only integrity hardening for V2.6.9.
-- This migration does not enable ROLE Runtime, create ROLE Tasks/Candidate Pools,
-- call a Role Directory, or modify Investment.
USE enterprise_platform;

-- Fail before permanent DDL when existing rows cannot satisfy the hardened model.
-- updated_time/version are the only available V2.6.9 mutation evidence; no data is repaired.
CREATE TEMPORARY TABLE tmp_role_runtime_v2610_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_runtime_v2610_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_runtime_v2610_guard (violation_count)
SELECT
    (SELECT COUNT(*)
       FROM role_runtime_binding_approval
      WHERE CHAR_LENGTH(TRIM(resolver_code)) = 0
         OR CHAR_LENGTH(TRIM(resolver_version)) = 0)
  + (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot
      WHERE CHAR_LENGTH(TRIM(resolver_code)) = 0
         OR CHAR_LENGTH(TRIM(resolver_version)) = 0)
  + (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot
      WHERE version <> 0 OR updated_time <> created_time)
  + (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot snapshot
       JOIN role_runtime_binding_approval approval ON approval.id = snapshot.approval_id
      WHERE approval.status <> 'APPROVED' OR approval.deleted <> 0)
  + (SELECT COUNT(*)
       FROM role_runtime_binding_approval
      WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));

DROP TEMPORARY TABLE tmp_role_runtime_v2610_guard;

ALTER TABLE role_runtime_binding_approval
    ADD CONSTRAINT chk_role_runtime_approval_resolver_identity CHECK (
        CHAR_LENGTH(TRIM(resolver_code)) > 0
        AND CHAR_LENGTH(TRIM(resolver_version)) > 0
    );

ALTER TABLE workflow_role_runtime_binding_snapshot
    ADD CONSTRAINT chk_role_runtime_snapshot_resolver_identity CHECK (
        CHAR_LENGTH(TRIM(resolver_code)) > 0
        AND CHAR_LENGTH(TRIM(resolver_version)) > 0
    );

DELIMITER $$

CREATE TRIGGER trg_role_runtime_approval_no_update
BEFORE UPDATE ON role_runtime_binding_approval
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_APPROVAL_APPEND_ONLY';
END$$

CREATE TRIGGER trg_workflow_role_runtime_snapshot_insert_guard
BEFORE INSERT ON workflow_role_runtime_binding_snapshot
FOR EACH ROW
BEGIN
    DECLARE approved_status VARCHAR(30) DEFAULT NULL;
    DECLARE approved_resolver_code VARCHAR(64) DEFAULT NULL;
    DECLARE approved_resolver_version VARCHAR(64) DEFAULT NULL;
    DECLARE approved_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SELECT status, resolver_code, resolver_version, contract_hash
      INTO approved_status, approved_resolver_code,
           approved_resolver_version, approved_contract_hash
      FROM role_runtime_binding_approval
     WHERE id = NEW.approval_id AND deleted = 0
     LIMIT 1;

    IF approved_status IS NULL OR approved_status <> 'APPROVED' THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_RUNTIME_APPROVAL_NOT_APPROVED';
    END IF;

    IF approved_resolver_code <> NEW.resolver_code
       OR approved_resolver_version <> NEW.resolver_version
       OR approved_contract_hash <> NEW.contract_hash THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_RUNTIME_APPROVAL_CONTRACT_MISMATCH';
    END IF;

    IF NEW.binding_hash NOT REGEXP '^[0-9a-f]{64}$'
       OR NEW.contract_hash NOT REGEXP '^[0-9a-f]{64}$' THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_RUNTIME_SNAPSHOT_HASH_INVALID';
    END IF;
END$$

CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_update
BEFORE UPDATE ON workflow_role_runtime_binding_snapshot
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_SNAPSHOT_IMMUTABLE';
END$$

CREATE TRIGGER trg_workflow_role_runtime_snapshot_no_delete
BEFORE DELETE ON workflow_role_runtime_binding_snapshot
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_SNAPSHOT_IMMUTABLE';
END$$

DELIMITER ;

