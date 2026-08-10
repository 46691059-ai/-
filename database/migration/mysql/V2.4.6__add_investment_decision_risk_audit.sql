-- Sprint 2-3.2 Investment decision condition, risk, and immutable audit enhancements.
-- Audit payloads store only approved summaries and hashes; Workflow remains the approval-record authority.
-- Rollback boundary: audit and condition history are records of fact and must use forward recovery.
USE enterprise_platform;

ALTER TABLE investment_decision_condition
    ADD COLUMN snapshot_id BIGINT NULL COMMENT '条件所属决策快照ID' AFTER decision_id,
    ADD COLUMN source_risk_ref VARCHAR(100) NULL COMMENT 'Risk中心来源风险稳定引用' AFTER condition_type,
    ADD COLUMN risk_level_snapshot VARCHAR(30) NULL COMMENT '条件形成时风险等级快照' AFTER source_risk_ref,
    ADD COLUMN waiver_workflow_binding_id BIGINT NULL COMMENT '条件豁免Workflow绑定ID' AFTER waiver_approval_ref,
    ADD COLUMN audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用' AFTER waiver_expire_date,
    ADD KEY idx_inv_condition_snapshot (snapshot_id, status, deleted),
    ADD KEY idx_inv_condition_risk (source_risk_ref, risk_level_snapshot, status, deleted),
    ADD KEY idx_inv_condition_waiver_workflow (waiver_workflow_binding_id, deleted),
    ADD CONSTRAINT fk_inv_condition_snapshot FOREIGN KEY (decision_id, snapshot_id)
        REFERENCES investment_decision_snapshot(decision_id, id),
    ADD CONSTRAINT fk_inv_condition_waiver_workflow FOREIGN KEY (waiver_workflow_binding_id)
        REFERENCES investment_workflow_binding(id),
    ADD CONSTRAINT chk_inv_condition_status CHECK (
        status IN (
            'OPEN', 'IN_PROGRESS', 'SUBMITTED', 'VERIFIED', 'CLOSED',
            'WAIVED', 'REJECTED', 'EXPIRED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT chk_inv_condition_review_result CHECK (
        review_result IS NULL OR review_result IN ('APPROVED', 'REJECTED', 'RETURNED')
    );

ALTER TABLE investment_decision_condition_action
    ADD COLUMN workflow_event_id VARCHAR(100) NULL COMMENT '触发动作的Workflow事件ID' AFTER idempotency_key,
    ADD COLUMN workflow_event_sequence BIGINT NULL COMMENT 'Workflow事件序号' AFTER workflow_event_id,
    ADD COLUMN payload_hash VARCHAR(128) NULL COMMENT '动作载荷规范化哈希' AFTER workflow_event_sequence,
    ADD COLUMN risk_ref VARCHAR(100) NULL COMMENT 'Risk中心稳定引用' AFTER payload_hash,
    ADD COLUMN audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用' AFTER risk_ref,
    ADD UNIQUE KEY uk_inv_condition_action_event (workflow_event_id, delete_token),
    ADD KEY idx_inv_condition_action_audit (audit_ref, action_time, deleted),
    ADD CONSTRAINT chk_inv_condition_action_to_status CHECK (
        to_status IN (
            'OPEN', 'IN_PROGRESS', 'SUBMITTED', 'VERIFIED', 'CLOSED',
            'WAIVED', 'REJECTED', 'EXPIRED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT chk_inv_condition_action_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'OPEN', 'IN_PROGRESS', 'SUBMITTED', 'VERIFIED', 'CLOSED',
            'WAIVED', 'REJECTED', 'EXPIRED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT chk_inv_condition_action_event_sequence CHECK (
        workflow_event_sequence IS NULL OR workflow_event_sequence >= 0
    );

CREATE TABLE investment_decision_audit_event (
    id BIGINT NOT NULL COMMENT '投资决策业务审计事件ID',
    event_no VARCHAR(100) NOT NULL COMMENT '业务审计事件编号',
    decision_id BIGINT NOT NULL COMMENT '投资决策ID',
    snapshot_id BIGINT NULL COMMENT '关联决策快照ID',
    decision_node_id BIGINT NULL COMMENT '关联决策节点ID',
    condition_id BIGINT NULL COMMENT '关联附条件任务ID',
    workflow_binding_id BIGINT NULL COMMENT '关联Workflow绑定ID',
    event_type VARCHAR(50) NOT NULL COMMENT '业务审计事件类型',
    source_system VARCHAR(50) NOT NULL COMMENT '事件来源系统',
    source_event_id VARCHAR(100) NULL COMMENT '来源系统事件ID',
    actor_type VARCHAR(30) NOT NULL COMMENT 'USER/SERVICE/SYSTEM',
    actor_id VARCHAR(100) NOT NULL COMMENT '操作主体稳定标识',
    actor_org_id BIGINT NULL COMMENT '操作主体组织ID',
    before_status VARCHAR(30) NULL COMMENT '变化前业务状态',
    after_status VARCHAR(30) NULL COMMENT '变化后业务状态',
    event_summary VARCHAR(2000) NULL COMMENT '脱敏事件摘要',
    payload_hash VARCHAR(128) NOT NULL COMMENT '事件载荷规范化哈希',
    risk_ref VARCHAR(100) NULL COMMENT 'Risk中心稳定引用',
    audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用',
    trace_id VARCHAR(64) NULL COMMENT '链路TraceId',
    occurred_time DATETIME(3) NOT NULL COMMENT '业务事件发生时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_decision_audit_event_no (event_no, delete_token),
    UNIQUE KEY uk_inv_decision_audit_source (source_system, source_event_id, delete_token),
    KEY idx_inv_decision_audit_decision (decision_id, occurred_time, deleted),
    KEY idx_inv_decision_audit_snapshot (snapshot_id, occurred_time, deleted),
    KEY idx_inv_decision_audit_workflow (workflow_binding_id, occurred_time, deleted),
    KEY idx_inv_decision_audit_trace (trace_id, occurred_time, deleted),
    KEY idx_inv_decision_audit_actor (actor_id, occurred_time, deleted),
    CONSTRAINT fk_inv_decision_audit_decision FOREIGN KEY (decision_id)
        REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_decision_audit_snapshot FOREIGN KEY (decision_id, snapshot_id)
        REFERENCES investment_decision_snapshot(decision_id, id),
    CONSTRAINT fk_inv_decision_audit_node FOREIGN KEY (decision_node_id)
        REFERENCES investment_decision_node(id),
    CONSTRAINT fk_inv_decision_audit_condition FOREIGN KEY (condition_id)
        REFERENCES investment_decision_condition(id),
    CONSTRAINT fk_inv_decision_audit_workflow FOREIGN KEY (workflow_binding_id)
        REFERENCES investment_workflow_binding(id),
    CONSTRAINT fk_inv_decision_audit_actor_org FOREIGN KEY (actor_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT chk_inv_decision_audit_actor CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    CONSTRAINT chk_inv_decision_audit_source CHECK (
        source_system IN ('INVESTMENT', 'WORKFLOW', 'RISK', 'AUDIT', 'SYSTEM')
    ),
    CONSTRAINT chk_inv_decision_audit_event_type CHECK (
        event_type IN (
            'SNAPSHOT_FROZEN', 'ROUTE_FROZEN', 'WORKFLOW_START_REQUESTED',
            'WORKFLOW_STARTED', 'NODE_ACTIVATED', 'NODE_COMPLETED',
            'PROCESS_RETURNED', 'PROCESS_COMPLETED', 'PROCESS_WITHDRAWN',
            'PROCESS_CANCELLED', 'PROCESS_TERMINATED', 'CONDITION_CREATED',
            'CONDITION_CHANGED', 'RISK_GATE_CHANGED', 'DECISION_STATE_CHANGED',
            'COMPENSATION_APPLIED'
        )
    ),
    CONSTRAINT chk_inv_decision_audit_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_inv_decision_audit_optimistic CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策不可变业务审计事件表';
