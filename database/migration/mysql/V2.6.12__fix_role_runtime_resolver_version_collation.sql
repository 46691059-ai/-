-- Sprint 2-3.7-WF5.7.5: forward-only Resolver Version collation hardening.
-- Historical evidence is never normalized, repaired, inferred, or deleted.
-- ROLE Runtime remains disabled; this migration changes persistence integrity only.
USE enterprise_platform;

-- Fail before permanent DDL when historical Resolver Versions are null, blank,
-- padded, lowercase, mixed case, non-ASCII, or otherwise outside the frozen format.
-- REGEXP_LIKE(..., 'c') makes the guard independent of each column/database collation.
CREATE TEMPORARY TABLE tmp_role_runtime_v2612_guard (
    violation_count BIGINT NOT NULL,
    CONSTRAINT chk_role_runtime_v2612_guard CHECK (violation_count = 0)
);

INSERT INTO tmp_role_runtime_v2612_guard (violation_count)
SELECT
    (SELECT COUNT(*)
       FROM role_runtime_binding_approval approval
      WHERE approval.resolver_version IS NULL
         OR CHAR_LENGTH(TRIM(approval.resolver_version)) = 0
         OR BINARY approval.resolver_version <> BINARY TRIM(approval.resolver_version)
         OR NOT REGEXP_LIKE(
                approval.resolver_version,
                '^[A-Z0-9][A-Z0-9_.-]{0,63}$',
                'c'
            ))
  + (SELECT COUNT(*)
       FROM workflow_role_runtime_binding_snapshot snapshot
      WHERE snapshot.resolver_version IS NULL
         OR CHAR_LENGTH(TRIM(snapshot.resolver_version)) = 0
         OR BINARY snapshot.resolver_version <> BINARY TRIM(snapshot.resolver_version)
         OR NOT REGEXP_LIKE(
                snapshot.resolver_version,
                '^[A-Z0-9][A-Z0-9_.-]{0,63}$',
                'c'
            ));

DROP TEMPORARY TABLE tmp_role_runtime_v2612_guard;

-- The persisted identity now has an explicit case-sensitive storage contract.
-- Reuse the existing constraint names so downstream schema checks stay stable.
ALTER TABLE role_runtime_binding_approval
    DROP CHECK chk_role_runtime_approval_resolver_identity,
    MODIFY COLUMN resolver_version VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen case-sensitive Resolver version',
    ADD CONSTRAINT chk_role_runtime_approval_resolver_identity CHECK (
        CHAR_LENGTH(TRIM(resolver_code)) > 0
        AND CHAR_LENGTH(TRIM(resolver_version)) > 0
        AND REGEXP_LIKE(
            resolver_version,
            '^[A-Z0-9][A-Z0-9_.-]{0,63}$',
            'c'
        )
    );

ALTER TABLE workflow_role_runtime_binding_snapshot
    DROP CHECK chk_role_runtime_snapshot_resolver_identity,
    MODIFY COLUMN resolver_version VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen case-sensitive Resolver version',
    ADD CONSTRAINT chk_role_runtime_snapshot_resolver_identity CHECK (
        CHAR_LENGTH(TRIM(resolver_code)) > 0
        AND CHAR_LENGTH(TRIM(resolver_version)) > 0
        AND REGEXP_LIKE(
            resolver_version,
            '^[A-Z0-9][A-Z0-9_.-]{0,63}$',
            'c'
        )
    );
