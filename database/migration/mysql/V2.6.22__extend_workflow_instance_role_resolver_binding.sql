-- RC2-S5: freeze published Version ROLE resolver bindings into Workflow instances.
-- Candidate resolution, Directory revision, Task, Claim and runtime enablement are out of scope.
USE enterprise_platform;

ALTER TABLE workflow_instance_resolver_binding_set
    MODIFY COLUMN manifest_version VARCHAR(64) NOT NULL
        COMMENT 'Frozen resolver manifest canonical version';

ALTER TABLE workflow_node_resolver_binding_snapshot
    DROP INDEX uk_workflow_node_resolver_binding_node,
    ADD COLUMN version_binding_id BIGINT NULL
        COMMENT 'Published workflow_version_node_resolver_binding source; NULL for Legacy USER'
        AFTER node_code_snapshot,
    ADD COLUMN version_binding_order INT NULL
        COMMENT 'Stable Version Binding order; NULL for Legacy USER'
        AFTER version_binding_id,
    ADD COLUMN version_binding_slot INT
        GENERATED ALWAYS AS (COALESCE(version_binding_order, 0)) STORED
        COMMENT '0 for Legacy USER; Version binding_order for ROLE'
        AFTER version_binding_order,
    ADD COLUMN version_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Published Version Binding SHA-256; NULL for Legacy USER'
        AFTER version_binding_order,
    ADD COLUMN resolver_contract_hash_snapshot VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Resolver contract frozen independently at Instance creation'
        AFTER version_binding_hash,
    ADD COLUMN role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Stable approval role code; ROLE only'
        AFTER resolver_contract_hash_snapshot,
    ADD COLUMN organization_scope_type VARCHAR(40)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Version Binding organization scope; ROLE only'
        AFTER role_code,
    ADD COLUMN resolved_organization_id BIGINT NULL
        COMMENT 'Resolved organization frozen without Directory access; ROLE only'
        AFTER organization_scope_type,
    ADD COLUMN effective_time_policy VARCHAR(40)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Candidate resolution time policy; ROLE only'
        AFTER resolved_organization_id,
    ADD COLUMN binding_schema_version VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Source Version Binding canonical schema; ROLE only'
        AFTER effective_time_policy,
    ADD UNIQUE KEY uk_workflow_node_resolver_binding_order (
        instance_id, node_id, version_binding_slot, delete_token
    ),
    ADD UNIQUE KEY uk_workflow_node_resolver_version_binding (
        instance_id, version_binding_id, delete_token
    ),
    ADD KEY idx_workflow_node_resolver_role_scope (
        role_code, resolved_organization_id, binding_status, deleted
    ),
    ADD CONSTRAINT fk_workflow_node_resolver_version_binding
        FOREIGN KEY (version_binding_id, definition_version_id, node_id)
        REFERENCES workflow_version_node_resolver_binding(
            id, definition_version_id, node_id
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_workflow_node_resolver_version_source CHECK (
        (
            strategy_type = 'USER'
            AND target_type = 'USER'
            AND resolver_mode = 'DIRECT'
            AND version_binding_id IS NULL
            AND version_binding_order IS NULL
            AND version_binding_hash IS NULL
            AND resolver_contract_hash_snapshot IS NULL
            AND role_code IS NULL
            AND organization_scope_type IS NULL
            AND resolved_organization_id IS NULL
            AND effective_time_policy IS NULL
            AND binding_schema_version IS NULL
        )
        OR
        (
            strategy_type = 'ROLE'
            AND target_type = 'ROLE'
            AND resolver_mode = 'CANDIDATE_POOL'
            AND version_binding_id IS NULL
            AND version_binding_order IS NULL
            AND version_binding_hash IS NULL
            AND resolver_contract_hash_snapshot IS NULL
            AND role_code IS NULL
            AND organization_scope_type IS NULL
            AND resolved_organization_id IS NULL
            AND effective_time_policy IS NULL
            AND binding_schema_version IS NULL
        )
        OR
        (
            strategy_type = 'ROLE'
            AND target_type = 'ROLE'
            AND resolver_mode = 'CANDIDATE_POOL'
            AND version_binding_id IS NOT NULL
            AND version_binding_order IS NOT NULL
            AND version_binding_order >= 1
            AND version_binding_hash IS NOT NULL
            AND version_binding_hash REGEXP '^[0-9a-f]{64}$'
            AND resolver_contract_hash_snapshot IS NOT NULL
            AND resolver_contract_hash_snapshot REGEXP '^[0-9a-f]{64}$'
            AND role_code IS NOT NULL
            AND REGEXP_LIKE(role_code, '^[A-Z][A-Z0-9_]{2,99}$', 'c')
            AND organization_scope_type IS NOT NULL
            AND organization_scope_type = 'FIXED_ORG'
            AND resolved_organization_id IS NOT NULL
            AND resolved_organization_id > 0
            AND effective_time_policy IS NOT NULL
            AND effective_time_policy = 'NODE_ACTIVATED_AT'
            AND binding_schema_version IS NOT NULL
            AND binding_schema_version = 'VERSION_NODE_RESOLVER_BINDING_V1'
        )
    );

DELIMITER $$

CREATE TRIGGER trg_workflow_node_resolver_snapshot_no_update
BEFORE UPDATE ON workflow_node_resolver_binding_snapshot
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE';
END$$

CREATE TRIGGER trg_workflow_node_resolver_snapshot_no_delete
BEFORE DELETE ON workflow_node_resolver_binding_snapshot
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE';
END$$

DELIMITER ;
