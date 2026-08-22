-- Sprint 2-3.7-WF5.7: ROLE Runtime persistence foundation only.
-- ROLE execution, Role Directory calls, Task creation and Candidate Pool creation remain disabled.
-- Requires the immutable V2.6.5-V2.6.8 Workflow binding/candidate/claim chain.
USE enterprise_platform;

CREATE TABLE role_runtime_binding_approval (
    id BIGINT NOT NULL COMMENT 'ROLE Runtime Binding approval ID',
    proposal_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Approved ROLE Binding Proposal SHA-256',
    eligibility_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Approved Runtime Eligibility SHA-256',
    resolver_code VARCHAR(64) NOT NULL COMMENT 'Frozen Resolver code',
    resolver_version VARCHAR(64) NOT NULL COMMENT 'Frozen Resolver version',
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen Resolver contract SHA-256',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/APPROVED/REJECTED/EXPIRED',
    approved_by VARCHAR(64) NULL COMMENT 'Approver stable user identifier',
    approved_at DATETIME(3) NULL COMMENT 'Approval decision time',
    reject_reason VARCHAR(500) NULL COMMENT 'Non-sensitive rejection reason',
    audit_info TEXT NOT NULL COMMENT 'Non-sensitive immutable governance evidence',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_runtime_approval_hash (
        proposal_hash, eligibility_hash, delete_token
    ),
    KEY idx_role_runtime_approval_resolver (
        resolver_code, resolver_version, status, deleted
    ),
    KEY idx_role_runtime_approval_status (status, approved_at, deleted),
    CONSTRAINT chk_role_runtime_approval_proposal_hash CHECK (
        proposal_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_role_runtime_approval_eligibility_hash CHECK (
        eligibility_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_role_runtime_approval_contract_hash CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_role_runtime_approval_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT chk_role_runtime_approval_decision CHECK (
        (status = 'PENDING'
            AND approved_by IS NULL AND approved_at IS NULL AND reject_reason IS NULL)
        OR
        (status = 'APPROVED'
            AND approved_by IS NOT NULL
            AND CHAR_LENGTH(TRIM(approved_by)) > 0
            AND approved_at IS NOT NULL AND reject_reason IS NULL)
        OR
        (status = 'REJECTED'
            AND approved_by IS NULL AND approved_at IS NULL
            AND reject_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(reject_reason)) > 0)
        OR
        (status = 'EXPIRED'
            AND approved_by IS NULL AND approved_at IS NULL AND reject_reason IS NULL)
    ),
    CONSTRAINT chk_role_runtime_approval_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_role_runtime_approval_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_role_runtime_approval_optimistic CHECK (version >= 0),
    CONSTRAINT chk_role_runtime_approval_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only ROLE Runtime Binding approval evidence';

CREATE TABLE workflow_role_runtime_binding_snapshot (
    id BIGINT NOT NULL COMMENT 'ROLE Runtime Binding Snapshot ID',
    approval_id BIGINT NOT NULL COMMENT 'Approved governance evidence ID',
    binding_set_id BIGINT NOT NULL COMMENT 'Frozen Resolver Binding Set ID',
    resolver_binding_id BIGINT NOT NULL COMMENT 'Frozen Resolver Binding ID',
    node_resolver_binding_id BIGINT NOT NULL COMMENT 'Frozen node Resolver Binding ID',
    instance_id BIGINT NOT NULL COMMENT 'Owning Workflow instance ID',
    definition_version_id BIGINT NOT NULL COMMENT 'Frozen Workflow version ID',
    node_id BIGINT NOT NULL COMMENT 'Owning Workflow node ID',
    resolver_code VARCHAR(64) NOT NULL COMMENT 'Frozen Resolver code',
    resolver_version VARCHAR(64) NOT NULL COMMENT 'Frozen Resolver version',
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen Resolver contract SHA-256',
    role_code VARCHAR(100) NOT NULL COMMENT 'Stable approval-role business code',
    organization_id VARCHAR(100) NOT NULL COMMENT 'Stable organization business identifier',
    directory_revision BIGINT NOT NULL COMMENT 'Frozen Role Directory revision',
    effective_at DATETIME(3) NOT NULL COMMENT 'Frozen Directory effective time',
    directory_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen Directory result SHA-256',
    role_rule_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen role rule SHA-256',
    candidate_rule_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen Candidate rule SHA-256',
    source_evidence_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Minimum source evidence SHA-256',
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1 SHA-256',
    status VARCHAR(30) NOT NULL DEFAULT 'FROZEN'
        COMMENT 'FROZEN/ARCHIVED/SECURITY_BLOCKED',
    audit_info TEXT NOT NULL COMMENT 'Non-sensitive immutable Runtime evidence',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_runtime_snapshot_approval (approval_id, delete_token),
    UNIQUE KEY uk_role_runtime_snapshot_node (instance_id, node_id, delete_token),
    UNIQUE KEY uk_role_runtime_snapshot_binding_hash (binding_hash, delete_token),
    KEY idx_role_runtime_snapshot_resolver (
        resolver_code, resolver_version, status, deleted
    ),
    KEY idx_role_runtime_snapshot_role (
        role_code, organization_id, effective_at, deleted
    ),
    KEY idx_role_runtime_snapshot_directory (
        directory_revision, directory_hash, deleted
    ),
    CONSTRAINT fk_role_runtime_snapshot_approval
        FOREIGN KEY (approval_id) REFERENCES role_runtime_binding_approval(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_runtime_snapshot_instance
        FOREIGN KEY (definition_version_id, instance_id)
        REFERENCES workflow_instance(version_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_runtime_snapshot_node
        FOREIGN KEY (definition_version_id, node_id)
        REFERENCES workflow_node(version_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_runtime_snapshot_binding_set
        FOREIGN KEY (binding_set_id, instance_id, definition_version_id)
        REFERENCES workflow_instance_resolver_binding_set(
            id, instance_id, definition_version_id
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_runtime_snapshot_resolver_binding
        FOREIGN KEY (resolver_binding_id, binding_set_id, instance_id)
        REFERENCES workflow_instance_resolver_binding(id, binding_set_id, instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_runtime_snapshot_node_binding
        FOREIGN KEY (
            node_resolver_binding_id, instance_id, node_id, resolver_binding_id
        ) REFERENCES workflow_node_resolver_binding_snapshot(
            id, instance_id, node_id, resolver_binding_id
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_role_runtime_snapshot_revision CHECK (directory_revision > 0),
    CONSTRAINT chk_role_runtime_snapshot_hashes CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$'
        AND directory_hash REGEXP '^[0-9a-f]{64}$'
        AND role_rule_hash REGEXP '^[0-9a-f]{64}$'
        AND candidate_rule_hash REGEXP '^[0-9a-f]{64}$'
        AND source_evidence_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_role_runtime_snapshot_business_keys CHECK (
        CHAR_LENGTH(TRIM(role_code)) > 0
        AND CHAR_LENGTH(TRIM(organization_id)) > 0
    ),
    CONSTRAINT chk_role_runtime_snapshot_status CHECK (
        status IN ('FROZEN', 'ARCHIVED', 'SECURITY_BLOCKED')
    ),
    CONSTRAINT chk_role_runtime_snapshot_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_role_runtime_snapshot_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_role_runtime_snapshot_optimistic CHECK (version >= 0),
    CONSTRAINT chk_role_runtime_snapshot_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable ROLE Runtime Binding and Directory evidence snapshot';
