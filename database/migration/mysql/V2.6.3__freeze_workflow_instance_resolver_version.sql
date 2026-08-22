-- Sprint 2-3.7-WF3.6.4: freeze resolver identity on every linear Workflow Instance.
-- Candidate only. Does not modify V2.6.2 assignment snapshots or enable new strategy types.
USE enterprise_platform;

ALTER TABLE workflow_instance
    ADD COLUMN resolver_code VARCHAR(64) NULL
        COMMENT 'Frozen resolver stable code' AFTER definition_content_hash_snapshot,
    ADD COLUMN resolver_version VARCHAR(64) NULL
        COMMENT 'Frozen resolver contract version' AFTER resolver_code,
    ADD COLUMN resolver_contract_hash CHAR(64) NULL
        COMMENT 'Frozen lowercase SHA-256 resolver contract hash' AFTER resolver_version;

-- V2.6.2 and earlier could execute only EXPLICIT_USER_V1 for linear instances.
-- This backfills the instance binding only; historical Tasks and assignment snapshots are untouched.
UPDATE workflow_instance
SET resolver_code = 'EXPLICIT_USER',
    resolver_version = 'EXPLICIT_USER_V1',
    resolver_contract_hash = '65873eb742d0a20b68f1a5e69cbc8e002eb6de26cfc7b516cbad494aa5ff5b6d'
WHERE engine_mode = 'MULTI_NODE_LINEAR_V1'
  AND resolver_code IS NULL
  AND resolver_version IS NULL
  AND resolver_contract_hash IS NULL;

ALTER TABLE workflow_instance
    ADD KEY idx_workflow_instance_resolver (
        resolver_code, resolver_version, status, deleted
    ),
    ADD CONSTRAINT chk_workflow_instance_resolver_complete CHECK (
        (resolver_code IS NULL AND resolver_version IS NULL AND resolver_contract_hash IS NULL)
        OR
        (resolver_code IS NOT NULL AND resolver_version IS NOT NULL
            AND resolver_contract_hash IS NOT NULL)
    ),
    ADD CONSTRAINT chk_workflow_instance_linear_resolver CHECK (
        engine_mode <> 'MULTI_NODE_LINEAR_V1'
        OR (resolver_code IS NOT NULL AND resolver_version IS NOT NULL
            AND resolver_contract_hash IS NOT NULL)
    ),
    ADD CONSTRAINT chk_workflow_instance_resolver_hash CHECK (
        resolver_contract_hash IS NULL
        OR resolver_contract_hash REGEXP '^[0-9a-f]{64}$'
    );
