-- Sprint 2-3.2 Investment decision immutable snapshot model.
-- MySQL 8.x; requires V2.4.0. Existing decision records remain valid with a NULL current_snapshot_id.
-- Rollback boundary: forward recovery after application writes; never drop populated snapshots.
USE enterprise_platform;

CREATE TABLE investment_decision_snapshot (
    id BIGINT NOT NULL COMMENT '决策快照ID',
    decision_id BIGINT NOT NULL COMMENT '投资决策ID',
    snapshot_version INT NOT NULL COMMENT '决策内快照版本，从1递增',
    snapshot_status VARCHAR(30) NOT NULL DEFAULT 'FROZEN' COMMENT 'FROZEN/SUPERSEDED',
    decision_package_version INT NOT NULL COMMENT '决策材料包版本',
    scheme_version_id BIGINT NOT NULL COMMENT '冻结投资方案版本ID',
    scheme_content_hash VARCHAR(128) NOT NULL COMMENT '投资方案内容哈希',
    feasibility_version_id BIGINT NOT NULL COMMENT '冻结可研版本ID',
    feasibility_content_hash VARCHAR(128) NOT NULL COMMENT '可研内容哈希',
    due_diligence_package_id BIGINT NOT NULL COMMENT '冻结尽调包ID',
    due_diligence_content_hash VARCHAR(128) NOT NULL COMMENT '尽调包内容哈希',
    route_code VARCHAR(50) NOT NULL COMMENT '冻结决策路线编码',
    route_rule_version VARCHAR(50) NOT NULL COMMENT '路线规则版本',
    route_snapshot_hash VARCHAR(128) NOT NULL COMMENT '决策路线快照哈希',
    major_decision_applicable SMALLINT NOT NULL DEFAULT 0 COMMENT '是否属于三重一大',
    party_pre_study_required SMALLINT NOT NULL DEFAULT 0 COMMENT '是否需要党委前置研究',
    final_decision_body VARCHAR(50) NOT NULL COMMENT '最终决策主体',
    risk_level_snapshot VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' COMMENT '风险等级快照',
    risk_assessment_ref VARCHAR(100) NULL COMMENT 'Risk中心风险评估稳定引用',
    risk_gate_result VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '风险门禁结果',
    risk_rule_version VARCHAR(50) NULL COMMENT '风险规则版本',
    risk_snapshot_hash VARCHAR(128) NULL COMMENT '风险结论快照哈希',
    audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用',
    snapshot_hash VARCHAR(128) NOT NULL COMMENT '完整决策快照哈希',
    frozen_by BIGINT NOT NULL COMMENT '冻结操作用户ID',
    frozen_time DATETIME(3) NOT NULL COMMENT '冻结时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_decision_snapshot_version (decision_id, snapshot_version, delete_token),
    UNIQUE KEY uk_inv_decision_snapshot_hash (decision_id, snapshot_hash, delete_token),
    UNIQUE KEY uk_inv_decision_snapshot_owner (decision_id, id),
    KEY idx_inv_decision_snapshot_status (decision_id, snapshot_status, deleted),
    KEY idx_inv_decision_snapshot_scheme (scheme_version_id, deleted),
    KEY idx_inv_decision_snapshot_feasibility (feasibility_version_id, deleted),
    KEY idx_inv_decision_snapshot_dd (due_diligence_package_id, deleted),
    KEY idx_inv_decision_snapshot_risk (risk_gate_result, risk_level_snapshot, deleted),
    KEY idx_inv_decision_snapshot_frozen_by (frozen_by, frozen_time, deleted),
    CONSTRAINT fk_inv_decision_snapshot_decision FOREIGN KEY (decision_id)
        REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_decision_snapshot_scheme FOREIGN KEY (scheme_version_id)
        REFERENCES investment_scheme_version(id),
    CONSTRAINT fk_inv_decision_snapshot_feasibility FOREIGN KEY (feasibility_version_id)
        REFERENCES investment_feasibility_version(id),
    CONSTRAINT fk_inv_decision_snapshot_dd FOREIGN KEY (due_diligence_package_id)
        REFERENCES investment_due_diligence_package(id),
    CONSTRAINT fk_inv_decision_snapshot_frozen_by FOREIGN KEY (frozen_by)
        REFERENCES sys_user(id),
    CONSTRAINT chk_inv_decision_snapshot_version CHECK (snapshot_version > 0 AND decision_package_version > 0),
    CONSTRAINT chk_inv_decision_snapshot_status CHECK (snapshot_status IN ('FROZEN', 'SUPERSEDED')),
    CONSTRAINT chk_inv_decision_snapshot_flags CHECK (
        major_decision_applicable IN (0, 1) AND party_pre_study_required IN (0, 1)
    ),
    CONSTRAINT chk_inv_decision_snapshot_risk CHECK (
        risk_gate_result IN ('PENDING', 'PASSED', 'CONDITIONAL', 'BLOCKED', 'NOT_REQUIRED')
    ),
    CONSTRAINT chk_inv_decision_snapshot_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_inv_decision_snapshot_optimistic CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策不可变快照表';

ALTER TABLE investment_decision
    ADD COLUMN current_snapshot_id BIGINT NULL COMMENT '当前提交使用的冻结快照ID' AFTER supersedes_decision_id,
    ADD COLUMN risk_level_snapshot VARCHAR(30) NULL COMMENT '当前决策风险等级快照' AFTER current_snapshot_id,
    ADD COLUMN risk_assessment_ref VARCHAR(100) NULL COMMENT 'Risk中心风险评估稳定引用' AFTER risk_level_snapshot,
    ADD COLUMN risk_gate_result VARCHAR(30) NULL COMMENT '风险门禁结果' AFTER risk_assessment_ref,
    ADD COLUMN risk_rule_version VARCHAR(50) NULL COMMENT '风险规则版本' AFTER risk_gate_result,
    ADD COLUMN risk_snapshot_hash VARCHAR(128) NULL COMMENT '风险结论快照哈希' AFTER risk_rule_version,
    ADD COLUMN audit_ref VARCHAR(100) NULL COMMENT 'Audit中心稳定引用' AFTER risk_snapshot_hash,
    ADD KEY idx_inv_decision_current_snapshot (current_snapshot_id, deleted),
    ADD KEY idx_inv_decision_snapshot_owner (id, current_snapshot_id),
    ADD KEY idx_inv_decision_risk_gate (risk_gate_result, risk_level_snapshot, deleted),
    ADD CONSTRAINT fk_inv_decision_current_snapshot FOREIGN KEY (id, current_snapshot_id)
        REFERENCES investment_decision_snapshot(decision_id, id),
    ADD CONSTRAINT chk_inv_decision_approval_status CHECK (
        approval_status IN (
            'NOT_SUBMITTED', 'DRAFT', 'MATERIAL_REVIEW', 'ROUTE_CONFIRMED',
            'SUBMITTING', 'IN_WORKFLOW', 'IN_DECISION', 'CONDITION_PENDING',
            'CONDITIONAL_PENDING', 'APPROVED', 'REJECTED', 'RETURNED',
            'DEFERRED', 'WITHDRAWN', 'SUPERSEDED', 'COMPLETED'
        )
    ),
    ADD CONSTRAINT chk_inv_decision_risk_gate CHECK (
        risk_gate_result IS NULL OR risk_gate_result IN (
            'PENDING', 'PASSED', 'CONDITIONAL', 'BLOCKED', 'NOT_REQUIRED'
        )
    );
