-- Sprint 2-3.7-WF3.6.4.2: enforce canonical lowercase resolver contract hashes.
-- V2.6.3 is immutable; this migration repairs its case-insensitive hash storage incrementally.
USE enterprise_platform;

-- V2.6.3 could accept upper/mixed-case hexadecimal under utf8mb4_general_ci.
-- Lowercasing is representation normalization only: the underlying SHA-256 value is unchanged.
UPDATE workflow_instance
SET resolver_contract_hash = LOWER(resolver_contract_hash)
WHERE resolver_contract_hash IS NOT NULL
  AND CAST(resolver_contract_hash AS BINARY) <> CAST(LOWER(resolver_contract_hash) AS BINARY);

ALTER TABLE workflow_instance
    MODIFY COLUMN resolver_contract_hash VARCHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL
        COMMENT 'Frozen lowercase SHA-256 resolver contract hash';
