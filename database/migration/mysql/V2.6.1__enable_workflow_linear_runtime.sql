-- Sprint 2-3.7-WF3.2: bind linear runtime instances, executions and tasks.
-- Candidate only. Requires immutable V2.6.0. Does not implement assignment strategies.
USE enterprise_platform;

ALTER TABLE workflow_version
    ADD COLUMN engine_mode VARCHAR(40) NOT NULL DEFAULT 'SINGLE_NODE_LEGACY'
        COMMENT 'SINGLE_NODE_LEGACY/MULTI_NODE_LINEAR_V1' AFTER schema_version,
    ADD COLUMN content_hash_algorithm VARCHAR(40) NOT NULL DEFAULT 'NODE_V1_SHA256'
        COMMENT 'NODE_V1_SHA256/GRAPH_V2_SHA256' AFTER engine_mode,
    ADD CONSTRAINT chk_workflow_version_engine_mode CHECK (
        engine_mode IN ('SINGLE_NODE_LEGACY', 'MULTI_NODE_LINEAR_V1')
    ),
    ADD CONSTRAINT chk_workflow_version_hash_algorithm CHECK (
        content_hash_algorithm IN ('NODE_V1_SHA256', 'GRAPH_V2_SHA256')
    ),
    ADD CONSTRAINT chk_workflow_version_engine_hash_pair CHECK (
        (engine_mode = 'SINGLE_NODE_LEGACY' AND content_hash_algorithm = 'NODE_V1_SHA256')
        OR (engine_mode = 'MULTI_NODE_LINEAR_V1' AND content_hash_algorithm = 'GRAPH_V2_SHA256')
    );

ALTER TABLE workflow_version_release
    ADD COLUMN engine_mode VARCHAR(40) NOT NULL DEFAULT 'SINGLE_NODE_LEGACY'
        COMMENT 'Frozen engine mode' AFTER content_hash,
    ADD COLUMN content_hash_algorithm VARCHAR(40) NOT NULL DEFAULT 'NODE_V1_SHA256'
        COMMENT 'Frozen content hash algorithm' AFTER engine_mode,
    ADD CONSTRAINT chk_workflow_release_engine_mode CHECK (
        engine_mode IN ('SINGLE_NODE_LEGACY', 'MULTI_NODE_LINEAR_V1')
    ),
    ADD CONSTRAINT chk_workflow_release_hash_algorithm CHECK (
        content_hash_algorithm IN ('NODE_V1_SHA256', 'GRAPH_V2_SHA256')
    ),
    ADD CONSTRAINT chk_workflow_release_engine_hash_pair CHECK (
        (engine_mode = 'SINGLE_NODE_LEGACY' AND content_hash_algorithm = 'NODE_V1_SHA256')
        OR (engine_mode = 'MULTI_NODE_LINEAR_V1' AND content_hash_algorithm = 'GRAPH_V2_SHA256')
    );

ALTER TABLE workflow_node_execution
    ADD UNIQUE KEY uk_workflow_node_execution_runtime_owner (instance_id, version_id, node_id, id);

ALTER TABLE workflow_instance
    ADD COLUMN engine_mode VARCHAR(40) NOT NULL DEFAULT 'SINGLE_NODE_LEGACY'
        COMMENT 'Frozen runtime engine mode' AFTER current_node_id,
    ADD COLUMN content_hash_algorithm_snapshot VARCHAR(40) NOT NULL DEFAULT 'NODE_V1_SHA256'
        COMMENT 'Frozen publication hash algorithm' AFTER engine_mode,
    ADD COLUMN current_node_execution_id BIGINT NULL
        COMMENT 'Authoritative active execution for linear runtime' AFTER content_hash_algorithm_snapshot,
    ADD KEY idx_workflow_instance_current_execution (current_node_execution_id, status, deleted),
    ADD CONSTRAINT fk_workflow_instance_current_execution
        FOREIGN KEY (id, current_node_execution_id)
        REFERENCES workflow_node_execution(instance_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_workflow_instance_engine_mode CHECK (
        engine_mode IN ('SINGLE_NODE_LEGACY', 'MULTI_NODE_LINEAR_V1')
    ),
    ADD CONSTRAINT chk_workflow_instance_hash_algorithm CHECK (
        content_hash_algorithm_snapshot IN ('NODE_V1_SHA256', 'GRAPH_V2_SHA256')
    ),
    ADD CONSTRAINT chk_workflow_instance_engine_hash_pair CHECK (
        (engine_mode = 'SINGLE_NODE_LEGACY'
            AND content_hash_algorithm_snapshot = 'NODE_V1_SHA256'
            AND current_node_execution_id IS NULL)
        OR (engine_mode = 'MULTI_NODE_LINEAR_V1'
            AND content_hash_algorithm_snapshot = 'GRAPH_V2_SHA256')
    );

ALTER TABLE workflow_task
    ADD COLUMN node_execution_id BIGINT NULL
        COMMENT 'Owning node execution; NULL only for legacy tasks' AFTER node_id,
    ADD UNIQUE KEY uk_workflow_task_execution_participant (
        node_execution_id, task_round, participant_key, delete_token
    ),
    ADD KEY idx_workflow_task_execution (node_execution_id, status, deleted),
    ADD CONSTRAINT fk_workflow_task_execution
        FOREIGN KEY (instance_id, version_id, node_id, node_execution_id)
        REFERENCES workflow_node_execution(instance_id, version_id, node_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
