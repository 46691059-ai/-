-- Sprint 2-3.7-WF3.9: freeze exact resolver contracts and node assignment rules per instance.
-- Candidate only. Requires immutable V2.6.4. Does not backfill legacy node evidence.
USE enterprise_platform;

CREATE TABLE workflow_instance_resolver_binding_set (
    id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    manifest_version VARCHAR(32) NOT NULL,
    manifest_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_count INT NOT NULL,
    binding_status VARCHAR(30) NOT NULL DEFAULT 'FROZEN',
    frozen_time DATETIME(3) NOT NULL,
    audit_info TEXT NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_resolver_binding_set_instance (instance_id, delete_token),
    UNIQUE KEY uk_workflow_resolver_binding_set_owner (id, instance_id, definition_version_id),
    KEY idx_workflow_resolver_binding_set_version (definition_version_id, binding_status, deleted),
    KEY idx_workflow_resolver_binding_set_manifest (manifest_hash, deleted),
    CONSTRAINT fk_workflow_resolver_binding_set_instance
        FOREIGN KEY (definition_version_id, instance_id)
        REFERENCES workflow_instance(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_resolver_binding_set_version
        FOREIGN KEY (definition_id, definition_version_id)
        REFERENCES workflow_version(definition_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_resolver_binding_set_manifest CHECK (
        manifest_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_resolver_binding_set_count CHECK (binding_count > 0),
    CONSTRAINT chk_workflow_resolver_binding_set_status CHECK (
        binding_status IN ('FROZEN', 'ARCHIVED', 'SECURITY_BLOCKED')
    ),
    CONSTRAINT chk_workflow_resolver_binding_set_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_workflow_resolver_binding_set_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_resolver_binding_set_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_resolver_binding_set_delete_token CHECK (
        (deleted = 0 AND delete_token = 0) OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Frozen resolver binding manifest for a Workflow instance';

CREATE TABLE workflow_instance_resolver_binding (
    id BIGINT NOT NULL,
    binding_set_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    resolver_code VARCHAR(64) NOT NULL,
    resolver_version VARCHAR(64) NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    resolver_mode VARCHAR(30) NOT NULL,
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_status VARCHAR(30) NOT NULL DEFAULT 'FROZEN',
    frozen_time DATETIME(3) NOT NULL,
    audit_info TEXT NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_resolver_binding_contract (
        binding_set_id, resolver_code, resolver_version, delete_token
    ),
    UNIQUE KEY uk_workflow_resolver_binding_owner (id, binding_set_id, instance_id),
    KEY idx_workflow_resolver_binding_runtime (instance_id, strategy_type, deleted),
    KEY idx_workflow_resolver_binding_impact (
        resolver_code, resolver_version, binding_status, deleted
    ),
    CONSTRAINT fk_workflow_resolver_binding_set
        FOREIGN KEY (binding_set_id, instance_id, definition_version_id)
        REFERENCES workflow_instance_resolver_binding_set(id, instance_id, definition_version_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_resolver_binding_strategy CHECK (
        strategy_type IN ('USER', 'ROLE', 'POSITION', 'ORG')
    ),
    CONSTRAINT chk_workflow_resolver_binding_mode CHECK (resolver_mode IN ('DIRECT')),
    CONSTRAINT chk_workflow_resolver_binding_contract_hash CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_resolver_binding_rule_hash CHECK (
        rule_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_resolver_binding_status CHECK (
        binding_status IN ('FROZEN', 'ARCHIVED', 'SECURITY_BLOCKED')
    ),
    CONSTRAINT chk_workflow_resolver_binding_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_workflow_resolver_binding_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_resolver_binding_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_resolver_binding_delete_token CHECK (
        (deleted = 0 AND delete_token = 0) OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Exact resolver contracts frozen by an instance';

CREATE TABLE workflow_node_resolver_binding_snapshot (
    id BIGINT NOT NULL,
    binding_set_id BIGINT NOT NULL,
    resolver_binding_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_version_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    node_code_snapshot VARCHAR(100) NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    resolver_mode VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_value_snapshot TEXT NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    rule_snapshot TEXT NOT NULL,
    rule_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_status VARCHAR(30) NOT NULL DEFAULT 'FROZEN',
    frozen_time DATETIME(3) NOT NULL,
    audit_info TEXT NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_resolver_binding_node (instance_id, node_id, delete_token),
    UNIQUE KEY uk_workflow_node_resolver_binding_owner (
        id, instance_id, node_id, resolver_binding_id
    ),
    KEY idx_workflow_node_resolver_binding_code (instance_id, node_code_snapshot, deleted),
    KEY idx_workflow_node_resolver_binding_resolver (
        resolver_binding_id, binding_status, deleted
    ),
    CONSTRAINT fk_workflow_node_resolver_binding_node
        FOREIGN KEY (definition_version_id, node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_node_resolver_binding_set
        FOREIGN KEY (binding_set_id, instance_id, definition_version_id)
        REFERENCES workflow_instance_resolver_binding_set(id, instance_id, definition_version_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_node_resolver_binding_resolver
        FOREIGN KEY (resolver_binding_id, binding_set_id, instance_id)
        REFERENCES workflow_instance_resolver_binding(id, binding_set_id, instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_node_resolver_binding_strategy CHECK (
        strategy_type = 'USER' AND target_type = 'USER'
    ),
    CONSTRAINT chk_workflow_node_resolver_binding_mode CHECK (resolver_mode = 'DIRECT'),
    CONSTRAINT chk_workflow_node_resolver_binding_target CHECK (
        CHAR_LENGTH(TRIM(target_value_snapshot)) > 0
    ),
    CONSTRAINT chk_workflow_node_resolver_binding_rule CHECK (
        CHAR_LENGTH(TRIM(rule_snapshot)) > 0
        AND rule_hash REGEXP '^[0-9a-f]{64}$'
        AND node_binding_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_node_resolver_binding_status CHECK (
        binding_status IN ('FROZEN', 'ARCHIVED', 'SECURITY_BLOCKED')
    ),
    CONSTRAINT chk_workflow_node_resolver_binding_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_workflow_node_resolver_binding_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_node_resolver_binding_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_node_resolver_binding_delete_token CHECK (
        (deleted = 0 AND delete_token = 0) OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable node resolver and assignment rule snapshots';
