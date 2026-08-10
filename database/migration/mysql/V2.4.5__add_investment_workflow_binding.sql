-- Sprint 2-3.2 Investment-to-Workflow binding and decision-node integration.
-- Workflow identifiers are opaque references. No cross-database foreign key is permitted.
-- Rollback boundary: forward recovery after external workflow instances have been bound.
USE enterprise_platform;

CREATE TABLE investment_workflow_binding (
    id BIGINT NOT NULL COMMENT 'Workflow绑定ID',
    decision_id BIGINT NOT NULL COMMENT '投资决策ID',
    snapshot_id BIGINT NOT NULL COMMENT '本次提交冻结快照ID',
    snapshot_hash VARCHAR(128) NOT NULL COMMENT '本次提交冻结快照哈希',
    attempt_no INT NOT NULL COMMENT '提交尝试序号，从1递增',
    business_type VARCHAR(50) NOT NULL DEFAULT 'INVESTMENT_DECISION' COMMENT 'Workflow业务类型',
    business_id VARCHAR(100) NOT NULL COMMENT '传递给Workflow的业务ID',
    business_key VARCHAR(200) NOT NULL COMMENT '稳定业务键',
    enterprise_id BIGINT NOT NULL COMMENT '企业隔离组织ID',
    workflow_instance_id VARCHAR(100) NULL COMMENT 'Workflow实例稳定引用',
    definition_key VARCHAR(100) NOT NULL COMMENT 'Workflow流程定义键',
    definition_version INT NOT NULL COMMENT 'Workflow流程定义版本',
    idempotency_key VARCHAR(200) NOT NULL COMMENT '启动流程幂等键',
    request_hash VARCHAR(128) NOT NULL COMMENT '启动请求规范化哈希',
    workflow_status VARCHAR(30) NOT NULL DEFAULT 'STARTING' COMMENT 'Workflow状态摘要',
    last_event_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '最后已应用事件序号',
    last_event_id VARCHAR(100) NULL COMMENT '最后已应用Workflow事件ID',
    last_synced_time DATETIME(3) NULL COMMENT '最后同步时间',
    started_time DATETIME(3) NULL COMMENT '流程开始时间',
    completed_time DATETIME(3) NULL COMMENT '流程结束时间',
    failure_code VARCHAR(100) NULL COMMENT '最近启动或同步失败原因码',
    risk_ref VARCHAR(100) NULL COMMENT 'Risk中心稳定引用',
    audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用',
    trace_id VARCHAR(64) NULL COMMENT '发起链路TraceId',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_workflow_decision_attempt (decision_id, attempt_no, delete_token),
    UNIQUE KEY uk_inv_workflow_snapshot_attempt (snapshot_id, attempt_no, delete_token),
    UNIQUE KEY uk_inv_workflow_idempotency (idempotency_key, delete_token),
    UNIQUE KEY uk_inv_workflow_instance (workflow_instance_id, delete_token),
    KEY idx_inv_workflow_business (business_type, business_id, attempt_no, deleted),
    KEY idx_inv_workflow_status (workflow_status, last_synced_time, deleted),
    KEY idx_inv_workflow_enterprise (enterprise_id, workflow_status, deleted),
    KEY idx_inv_workflow_event_watermark (workflow_instance_id, last_event_sequence, deleted),
    CONSTRAINT fk_inv_workflow_decision FOREIGN KEY (decision_id)
        REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_workflow_snapshot FOREIGN KEY (decision_id, snapshot_id)
        REFERENCES investment_decision_snapshot(decision_id, id),
    CONSTRAINT fk_inv_workflow_enterprise FOREIGN KEY (enterprise_id)
        REFERENCES sys_org(id),
    CONSTRAINT chk_inv_workflow_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_inv_workflow_definition_version CHECK (definition_version > 0),
    CONSTRAINT chk_inv_workflow_sequence CHECK (last_event_sequence >= 0),
    CONSTRAINT chk_inv_workflow_business_type CHECK (business_type = 'INVESTMENT_DECISION'),
    CONSTRAINT chk_inv_workflow_status CHECK (
        workflow_status IN (
            'STARTING', 'RUNNING', 'COMPLETED', 'RETURNED', 'WITHDRAWN',
            'CANCELLED', 'TERMINATED', 'START_FAILED'
        )
    ),
    CONSTRAINT chk_inv_workflow_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_inv_workflow_optimistic CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策与Workflow流程实例绑定表';

ALTER TABLE investment_decision_node
    ADD COLUMN snapshot_id BIGINT NULL COMMENT '节点所属决策快照ID' AFTER decision_id,
    ADD COLUMN workflow_binding_id BIGINT NULL COMMENT 'Workflow绑定ID' AFTER approval_instance_ref,
    ADD COLUMN workflow_node_instance_ref VARCHAR(100) NULL COMMENT 'Workflow节点实例引用' AFTER workflow_binding_id,
    ADD COLUMN workflow_task_ref VARCHAR(100) NULL COMMENT 'Workflow任务引用' AFTER workflow_node_instance_ref,
    ADD COLUMN last_workflow_event_id VARCHAR(100) NULL COMMENT '最后应用Workflow事件ID' AFTER workflow_task_ref,
    ADD COLUMN workflow_event_sequence BIGINT NULL COMMENT '最后应用Workflow事件序号' AFTER last_workflow_event_id,
    ADD COLUMN opinion_hash VARCHAR(128) NULL COMMENT '完整审批意见哈希' AFTER opinion_summary,
    ADD COLUMN risk_ref VARCHAR(100) NULL COMMENT 'Risk中心稳定引用' AFTER record_hash,
    ADD COLUMN audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用' AFTER risk_ref,
    ADD KEY idx_inv_decision_node_snapshot (snapshot_id, sequence_no, deleted),
    ADD KEY idx_inv_decision_node_binding (workflow_binding_id, status, deleted),
    ADD KEY idx_inv_decision_node_workflow_ref (workflow_node_instance_ref, deleted),
    ADD CONSTRAINT fk_inv_decision_node_snapshot FOREIGN KEY (decision_id, snapshot_id)
        REFERENCES investment_decision_snapshot(decision_id, id),
    ADD CONSTRAINT fk_inv_decision_node_binding FOREIGN KEY (workflow_binding_id)
        REFERENCES investment_workflow_binding(id),
    ADD CONSTRAINT chk_inv_decision_node_status CHECK (
        status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'SKIPPED', 'CORRECTED')
    ),
    ADD CONSTRAINT chk_inv_decision_node_result CHECK (
        result IS NULL OR result IN (
            'APPROVED', 'APPROVED_WITH_CONDITIONS', 'REJECTED',
            'RETURNED', 'DEFERRED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT chk_inv_decision_node_event_sequence CHECK (
        workflow_event_sequence IS NULL OR workflow_event_sequence >= 0
    );
