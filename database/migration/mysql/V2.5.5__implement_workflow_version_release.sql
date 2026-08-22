-- Sprint 2-3.7-WF2.7: Workflow version release audit and runtime hash snapshot.
-- Candidate only. Do not edit V2.5.0 through V2.5.4.
USE enterprise_platform;

ALTER TABLE workflow_instance
    ADD COLUMN definition_content_hash_snapshot VARCHAR(128) NULL
        COMMENT 'Frozen published workflow content SHA-256' AFTER definition_version_no;

UPDATE workflow_instance wi
JOIN workflow_version wv
  ON wv.definition_id = wi.definition_id
 AND wv.id = wi.version_id
 AND wv.deleted = 0
SET wi.definition_content_hash_snapshot = wv.content_hash
WHERE wi.definition_content_hash_snapshot IS NULL;

-- Deliberately fails if a historical instance cannot be tied to publication evidence.
ALTER TABLE workflow_instance
    MODIFY COLUMN definition_content_hash_snapshot VARCHAR(128) NOT NULL
        COMMENT 'Frozen published workflow content SHA-256',
    ADD CONSTRAINT chk_workflow_instance_definition_hash CHECK (
        definition_content_hash_snapshot REGEXP '^[0-9a-f]{64}$'
    );

CREATE TABLE workflow_version_release (
    id BIGINT NOT NULL COMMENT 'Release audit ID',
    definition_id BIGINT NOT NULL COMMENT 'Workflow definition ID',
    previous_version_id BIGINT NULL COMMENT 'Previously published version ID',
    published_version_id BIGINT NOT NULL COMMENT 'Newly published version ID',
    published_version_no INT NOT NULL COMMENT 'Newly published version number snapshot',
    content_hash VARCHAR(128) NOT NULL COMMENT 'Published semantic SHA-256 snapshot',
    operator_user_id BIGINT NOT NULL COMMENT 'Publisher user ID',
    operator_org_id BIGINT NOT NULL COMMENT 'Publisher organization snapshot',
    published_time DATETIME(3) NOT NULL COMMENT 'Atomic switch time',
    trace_id VARCHAR(64) NULL COMMENT 'Request trace ID',
    validation_summary VARCHAR(1000) NULL COMMENT 'Non-sensitive publication validation summary',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag; release facts must remain active',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 for immutable active release fact',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_release_published (definition_id, published_version_id, delete_token),
    KEY idx_workflow_release_time (definition_id, published_time, deleted),
    KEY idx_workflow_release_previous (definition_id, previous_version_id, deleted),
    KEY idx_workflow_release_trace (trace_id, published_time),
    CONSTRAINT fk_workflow_release_definition FOREIGN KEY (definition_id)
        REFERENCES workflow_definition(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_release_previous FOREIGN KEY (definition_id, previous_version_id)
        REFERENCES workflow_version(definition_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_release_published FOREIGN KEY (definition_id, published_version_id)
        REFERENCES workflow_version(definition_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_release_version_no CHECK (published_version_no > 0),
    CONSTRAINT chk_workflow_release_hash CHECK (content_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT chk_workflow_release_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable Workflow version publication audit facts';
