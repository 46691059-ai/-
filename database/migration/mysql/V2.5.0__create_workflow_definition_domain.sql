-- Sprint 2-3.7-WF2.1: Workflow Lite definition-domain schema.
-- MySQL 8.x. Requires the governed V2.4.9 baseline.
-- This migration creates Workflow-owned tables only and does not access Investment data.
-- Rollback boundary: before any definition is published, an approved rollback may drop
-- the three new tables in reverse dependency order; after use, recovery must be forward-only.
USE enterprise_platform;

CREATE TABLE workflow_definition (
    id BIGINT NOT NULL COMMENT '流程定义ID',
    definition_code VARCHAR(100) NOT NULL COMMENT '企业内稳定流程编码',
    definition_name VARCHAR(200) NOT NULL COMMENT '流程名称',
    business_type VARCHAR(64) NOT NULL COMMENT '适用业务类型',
    enterprise_id BIGINT NOT NULL COMMENT '企业隔离标识，逻辑关联组织中心',
    owner_org_id BIGINT NULL COMMENT '管理归属组织，逻辑关联组织中心',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE/ARCHIVED',
    current_version_id BIGINT NULL COMMENT '当前已发布流程版本ID',
    description VARCHAR(1000) NULL COMMENT '流程用途和边界说明',
    created_by VARCHAR(64) NULL COMMENT '创建人稳定标识',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by VARCHAR(64) NULL COMMENT '更新人稳定标识',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '有效数据为0，逻辑删除后写入本行ID',
    remark VARCHAR(500) NULL COMMENT '非敏感备注',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_definition_code (enterprise_id, definition_code, delete_token),
    UNIQUE KEY uk_workflow_definition_current (id, current_version_id),
    KEY idx_workflow_definition_business (business_type, status, deleted),
    KEY idx_workflow_definition_owner (owner_org_id, status, deleted),
    KEY idx_workflow_definition_current_version (current_version_id, deleted),
    CONSTRAINT chk_workflow_definition_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT chk_workflow_definition_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_definition_version CHECK (version >= 0),
    CONSTRAINT chk_workflow_definition_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow流程定义表';

CREATE TABLE workflow_version (
    id BIGINT NOT NULL COMMENT '流程版本ID',
    definition_id BIGINT NOT NULL COMMENT '流程定义ID',
    version_no INT NOT NULL COMMENT '定义内递增版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
    schema_version VARCHAR(30) NOT NULL COMMENT '节点及规则Schema版本',
    content_hash VARCHAR(128) NULL COMMENT '发布内容SHA-256',
    change_note VARCHAR(1000) NULL COMMENT '版本变更说明',
    effective_from DATETIME(3) NULL COMMENT '版本生效时间',
    effective_to DATETIME(3) NULL COMMENT '停止创建新实例时间',
    published_by BIGINT NULL COMMENT '发布用户ID，逻辑关联用户中心',
    published_time DATETIME(3) NULL COMMENT '发布时间',
    source_version_id BIGINT NULL COMMENT '复制来源版本ID',
    created_by VARCHAR(64) NULL COMMENT '创建人稳定标识',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by VARCHAR(64) NULL COMMENT '更新人稳定标识',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '有效数据为0，逻辑删除后写入本行ID',
    remark VARCHAR(500) NULL COMMENT '非敏感备注',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version_no (definition_id, version_no, delete_token),
    UNIQUE KEY uk_workflow_version_hash (definition_id, content_hash, delete_token),
    UNIQUE KEY uk_workflow_version_owner (definition_id, id),
    KEY idx_workflow_version_status (definition_id, status, effective_from, deleted),
    KEY idx_workflow_version_source (definition_id, source_version_id, deleted),
    CONSTRAINT fk_workflow_version_definition FOREIGN KEY (definition_id)
        REFERENCES workflow_definition(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_version_source FOREIGN KEY (definition_id, source_version_id)
        REFERENCES workflow_version(definition_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_version_no CHECK (version_no > 0),
    CONSTRAINT chk_workflow_version_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT chk_workflow_version_publish_evidence CHECK (
        status = 'DRAFT'
        OR (content_hash IS NOT NULL AND published_by IS NOT NULL AND published_time IS NOT NULL)
    ),
    CONSTRAINT chk_workflow_version_effective_period CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT chk_workflow_version_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_version_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_version_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow流程版本表';

CREATE TABLE workflow_node (
    id BIGINT NOT NULL COMMENT '流程节点ID',
    version_id BIGINT NOT NULL COMMENT '流程版本ID',
    node_code VARCHAR(100) NOT NULL COMMENT '版本内稳定节点编码',
    node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(30) NOT NULL COMMENT 'APPROVAL/COUNTERSIGN/CONDITION',
    node_order INT NOT NULL COMMENT '线性基准顺序，从1开始',
    governance_node_type VARCHAR(40) NOT NULL DEFAULT 'GENERAL_APPROVAL'
        COMMENT 'GENERAL_APPROVAL/PARTY_PRE_STUDY/BOARD_DECISION/MANAGEMENT_DECISION',
    approval_mode VARCHAR(30) NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE/ALL/ANY/QUORUM',
    approval_threshold DECIMAL(5,2) NULL COMMENT 'QUORUM模式通过百分比',
    assignment_rule_type VARCHAR(30) NOT NULL COMMENT 'USER/ORG/POSITION/ORG_POSITION/RULE',
    assignment_rule_config TEXT NOT NULL COMMENT '规范化任务分配规则JSON',
    entry_condition_config TEXT NULL COMMENT '白名单进入条件JSON',
    completion_condition_config TEXT NULL COMMENT '白名单完成条件JSON',
    timeout_minutes INT NULL COMMENT '任务超时分钟数',
    withdraw_allowed SMALLINT NOT NULL DEFAULT 0 COMMENT '当前节点是否允许撤回',
    enabled SMALLINT NOT NULL DEFAULT 1 COMMENT '草稿节点是否启用',
    created_by VARCHAR(64) NULL COMMENT '创建人稳定标识',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_by VARCHAR(64) NULL COMMENT '更新人稳定标识',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '有效数据为0，逻辑删除后写入本行ID',
    remark VARCHAR(500) NULL COMMENT '非敏感备注',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_code (version_id, node_code, delete_token),
    UNIQUE KEY uk_workflow_node_order (version_id, node_order, delete_token),
    UNIQUE KEY uk_workflow_node_owner (version_id, id),
    KEY idx_workflow_node_type (version_id, node_type, enabled, deleted),
    KEY idx_workflow_node_governance (governance_node_type, deleted),
    CONSTRAINT fk_workflow_node_version FOREIGN KEY (version_id)
        REFERENCES workflow_version(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_node_type CHECK (
        node_type IN ('APPROVAL', 'COUNTERSIGN', 'CONDITION')
    ),
    CONSTRAINT chk_workflow_node_order CHECK (node_order > 0),
    CONSTRAINT chk_workflow_node_governance_type CHECK (
        governance_node_type IN (
            'GENERAL_APPROVAL', 'PARTY_PRE_STUDY',
            'BOARD_DECISION', 'MANAGEMENT_DECISION'
        )
    ),
    CONSTRAINT chk_workflow_node_approval_mode CHECK (
        approval_mode IN ('SINGLE', 'ALL', 'ANY', 'QUORUM')
    ),
    CONSTRAINT chk_workflow_node_approval_threshold CHECK (
        (approval_mode = 'QUORUM' AND approval_threshold > 0 AND approval_threshold <= 100)
        OR (approval_mode <> 'QUORUM' AND approval_threshold IS NULL)
    ),
    CONSTRAINT chk_workflow_node_assignment_type CHECK (
        assignment_rule_type IN ('USER', 'ORG', 'POSITION', 'ORG_POSITION', 'RULE')
    ),
    CONSTRAINT chk_workflow_node_timeout CHECK (
        timeout_minutes IS NULL OR timeout_minutes > 0
    ),
    CONSTRAINT chk_workflow_node_flags CHECK (
        withdraw_allowed IN (0, 1) AND enabled IN (0, 1)
    ),
    CONSTRAINT chk_workflow_node_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_node_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_node_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow流程节点表';

ALTER TABLE workflow_definition
    ADD CONSTRAINT fk_workflow_definition_current_version
        FOREIGN KEY (id, current_version_id)
        REFERENCES workflow_version(definition_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
