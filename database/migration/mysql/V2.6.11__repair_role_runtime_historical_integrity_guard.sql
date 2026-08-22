-- Sprint 2-3.7-WF5.7.3: forward-only historical ROLE Runtime evidence guard.
-- No evidence is deleted, repaired, backfilled, or inferred. ROLE Runtime stays disabled.
USE enterprise_platform;

-- The complete historical scan must pass before this migration creates any permanent
-- constraint or trigger. MySQL CHECK error 3819 is the intentional fail-closed signal.
CREATE TEMPORARY TABLE tmp_role_runtime_v2611_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_runtime_v2611_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_runtime_v2611_guard (violation_count)
SELECT
    -- Approval resolver identity and contract evidence must match the Domain contract.
    (SELECT COUNT(*)
       FROM role_runtime_binding_approval approval
      WHERE approval.resolver_code IS NULL
         OR approval.resolver_version IS NULL
         OR approval.contract_hash IS NULL
         OR CHAR_LENGTH(TRIM(approval.resolver_code)) = 0
         OR CHAR_LENGTH(TRIM(approval.resolver_version)) = 0
         OR approval.resolver_code <> TRIM(approval.resolver_code)
         OR approval.resolver_version <> TRIM(approval.resolver_version)
         OR approval.resolver_code NOT REGEXP '^[A-Z][A-Z0-9_]{2,63}$'
         OR approval.resolver_version NOT REGEXP '^[A-Z0-9][A-Z0-9_.-]{0,63}$'
         OR approval.contract_hash NOT REGEXP '^[0-9a-f]{64}$')
  + -- Snapshot resolver identity and all canonical inputs must be valid.
    (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot snapshot
      WHERE snapshot.resolver_code IS NULL
         OR snapshot.resolver_version IS NULL
         OR snapshot.contract_hash IS NULL
         OR snapshot.binding_hash IS NULL
         OR CHAR_LENGTH(TRIM(snapshot.resolver_code)) = 0
         OR CHAR_LENGTH(TRIM(snapshot.resolver_version)) = 0
         OR snapshot.resolver_code <> TRIM(snapshot.resolver_code)
         OR snapshot.resolver_version <> TRIM(snapshot.resolver_version)
         OR snapshot.resolver_code NOT REGEXP '^[A-Z][A-Z0-9_]{2,63}$'
         OR snapshot.resolver_version NOT REGEXP '^[A-Z0-9][A-Z0-9_.-]{0,63}$'
         OR snapshot.contract_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR snapshot.binding_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR snapshot.directory_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR snapshot.role_rule_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR snapshot.candidate_rule_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR snapshot.source_evidence_hash NOT REGEXP '^[0-9a-f]{64}$')
  + -- The Approval must exist, be active and APPROVED, and own the exact Resolver contract.
    (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot snapshot
       LEFT JOIN role_runtime_binding_approval approval
         ON approval.id = snapshot.approval_id
      WHERE approval.id IS NULL
         OR approval.deleted <> 0
         OR approval.status <> 'APPROVED'
         OR BINARY approval.resolver_code <> BINARY snapshot.resolver_code
         OR BINARY approval.resolver_version <> BINARY snapshot.resolver_version
         OR BINARY approval.contract_hash <> BINARY snapshot.contract_hash)
  + -- Recompute ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1 exactly as the Domain policy.
    (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot snapshot
      WHERE BINARY snapshot.binding_hash <> BINARY SHA2(
          CONCAT(
              '{"candidateRuleHash":"', snapshot.candidate_rule_hash,
              '","contractHash":"', snapshot.contract_hash,
              '","directoryHash":"', snapshot.directory_hash,
              '","directoryRevision":', CAST(snapshot.directory_revision AS CHAR),
              ',"effectiveAt":', JSON_QUOTE(
                  CASE
                      WHEN MICROSECOND(snapshot.effective_at) = 0 THEN
                          CONCAT(DATE_FORMAT(snapshot.effective_at, '%Y-%m-%dT%H:%i:%s'), 'Z')
                      ELSE
                          CONCAT(DATE_FORMAT(snapshot.effective_at, '%Y-%m-%dT%H:%i:%s.'),
                                 LPAD(MICROSECOND(snapshot.effective_at) DIV 1000, 3, '0'), 'Z')
                  END),
              ',"organizationId":', JSON_QUOTE(TRIM(snapshot.organization_id)),
              ',"resolverCode":"', TRIM(snapshot.resolver_code),
              '","resolverVersion":"', TRIM(snapshot.resolver_version),
              '","roleCode":', JSON_QUOTE(TRIM(snapshot.role_code)),
              ',"roleRuleHash":"', snapshot.role_rule_hash,
              '","schema":"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1"',
              ',"sourceEvidenceHash":"', snapshot.source_evidence_hash, '"}'
          ), 256
      ));

DROP TEMPORARY TABLE tmp_role_runtime_v2611_guard;

-- Append-only governance was incomplete in V2.6.10: Approval DELETE was not blocked.
DELIMITER $$

CREATE TRIGGER trg_role_runtime_approval_no_delete
BEFORE DELETE ON role_runtime_binding_approval
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'ROLE_RUNTIME_APPROVAL_APPEND_ONLY';
END$$

-- New Snapshot rows must satisfy the same canonical contract as historical rows.
CREATE TRIGGER trg_workflow_role_runtime_snapshot_canonical_guard
BEFORE INSERT ON workflow_role_runtime_binding_snapshot
FOR EACH ROW
BEGIN
    DECLARE computed_binding_hash VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SET computed_binding_hash = SHA2(
        CONCAT(
            '{"candidateRuleHash":"', NEW.candidate_rule_hash,
            '","contractHash":"', NEW.contract_hash,
            '","directoryHash":"', NEW.directory_hash,
            '","directoryRevision":', CAST(NEW.directory_revision AS CHAR),
            ',"effectiveAt":', JSON_QUOTE(
                CASE
                    WHEN MICROSECOND(NEW.effective_at) = 0 THEN
                        CONCAT(DATE_FORMAT(NEW.effective_at, '%Y-%m-%dT%H:%i:%s'), 'Z')
                    ELSE
                        CONCAT(DATE_FORMAT(NEW.effective_at, '%Y-%m-%dT%H:%i:%s.'),
                               LPAD(MICROSECOND(NEW.effective_at) DIV 1000, 3, '0'), 'Z')
                END),
            ',"organizationId":', JSON_QUOTE(TRIM(NEW.organization_id)),
            ',"resolverCode":"', TRIM(NEW.resolver_code),
            '","resolverVersion":"', TRIM(NEW.resolver_version),
            '","roleCode":', JSON_QUOTE(TRIM(NEW.role_code)),
            ',"roleRuleHash":"', NEW.role_rule_hash,
            '","schema":"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1"',
            ',"sourceEvidenceHash":"', NEW.source_evidence_hash, '"}'
        ), 256
    );

    IF computed_binding_hash IS NULL
       OR BINARY computed_binding_hash <> BINARY NEW.binding_hash THEN
        SIGNAL SQLSTATE '45000'
            SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'ROLE_RUNTIME_SNAPSHOT_CANONICAL_HASH_MISMATCH';
    END IF;
END$$

DELIMITER ;
