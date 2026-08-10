-- Investment decision-loop baseline. MySQL 8.4; requires the 07_investment.sql baseline.
-- Existing investment_project, investment_feasibility and investment_decision tables are
-- expanded in place. Historical rows are preserved and marked with compatible defaults.
-- Rollback boundary: use forward recovery after application writes; do not drop populated tables.
USE enterprise_platform;

ALTER TABLE investment_project
    ADD COLUMN investment_method VARCHAR(50) NULL COMMENT '出资方式' AFTER investment_type,
    ADD COLUMN cooperation_mode VARCHAR(50) NULL COMMENT '合作模式' AFTER investment_method,
    ADD COLUMN partner_summary VARCHAR(500) NULL COMMENT '合作方摘要' AFTER partner_name,
    ADD COLUMN investee_company_id BIGINT NULL COMMENT '被投企业或SPV ID' AFTER spv_company_id,
    ADD KEY idx_inv_project_status (status, risk_level, deleted),
    ADD KEY idx_inv_project_investee (investee_company_id, status, deleted),
    ADD CONSTRAINT fk_inv_project_investee FOREIGN KEY (investee_company_id)
        REFERENCES investment_company(id);

UPDATE investment_project
SET investment_method = 'LEGACY_UNKNOWN',
    partner_summary = partner_name,
    investee_company_id = spv_company_id
WHERE investment_method IS NULL;

ALTER TABLE investment_project
    MODIFY COLUMN investment_method VARCHAR(50) NOT NULL COMMENT '出资方式';

CREATE TABLE investment_opportunity (
    id BIGINT NOT NULL COMMENT '投资机会ID',
    opportunity_no VARCHAR(50) NOT NULL COMMENT '机会编号',
    investment_id BIGINT NULL COMMENT '转化后的投资事项ID',
    opportunity_name VARCHAR(200) NOT NULL COMMENT '机会名称',
    source_type VARCHAR(50) NOT NULL COMMENT '机会来源类型',
    source_description VARCHAR(500) NULL COMMENT '来源说明',
    proposing_org_id BIGINT NOT NULL COMMENT '提出组织ID',
    proposer_id BIGINT NULL COMMENT '提出人用户ID',
    investment_direction VARCHAR(200) NOT NULL COMMENT '投资方向',
    partner_summary VARCHAR(500) NULL COMMENT '候选合作方摘要',
    preliminary_amount DECIMAL(18,2) NULL COMMENT '初步投资额',
    preliminary_income DECIMAL(18,2) NULL COMMENT '初步收益',
    preliminary_roi DECIMAL(9,4) NULL COMMENT '初步ROI',
    preliminary_irr DECIMAL(9,4) NULL COMMENT '初步IRR',
    payback_period DECIMAL(9,2) NULL COMMENT '初步回收期',
    estimate_date DATE NULL COMMENT '测算基准日',
    estimate_assumption TEXT NULL COMMENT '测算假设',
    status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED' COMMENT '机会状态',
    screening_conclusion VARCHAR(1000) NULL COMMENT '研判结论',
    converted_time DATETIME(3) NULL COMMENT '转化时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_opportunity_no (opportunity_no, delete_token),
    KEY idx_inv_opportunity_org (proposing_org_id, status, deleted),
    KEY idx_inv_opportunity_source (source_type, status, deleted),
    KEY idx_inv_opportunity_investment (investment_id, deleted),
    CONSTRAINT fk_inv_opportunity_investment FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT fk_inv_opportunity_org FOREIGN KEY (proposing_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT chk_inv_opportunity_amount CHECK (
        preliminary_amount IS NULL OR preliminary_amount >= 0
    ),
    CONSTRAINT chk_inv_opportunity_payback CHECK (
        payback_period IS NULL OR payback_period >= 0
    ),
    CONSTRAINT chk_inv_opportunity_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资机会表';

ALTER TABLE investment_feasibility
    ADD COLUMN current_version_id BIGINT NULL COMMENT '当前工作版本ID' AFTER investment_id,
    ADD COLUMN current_frozen_version_id BIGINT NULL COMMENT '当前冻结版本ID' AFTER current_version_id,
    ADD COLUMN status VARCHAR(30) NULL COMMENT '可研档案状态' AFTER current_frozen_version_id;

UPDATE investment_feasibility
SET status = 'LEGACY'
WHERE status IS NULL;

ALTER TABLE investment_feasibility
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '可研档案状态',
    ADD KEY idx_inv_feasibility_status (status, deleted);

CREATE TABLE investment_feasibility_version (
    id BIGINT NOT NULL COMMENT '可研版本ID',
    feasibility_id BIGINT NOT NULL COMMENT '可研档案ID',
    version_no INT NOT NULL COMMENT '版本号',
    report_no VARCHAR(100) NULL COMMENT '报告编号',
    report_name VARCHAR(200) NOT NULL COMMENT '报告名称',
    compiler_type VARCHAR(20) NOT NULL COMMENT '编制方式',
    compiler_org_id BIGINT NULL COMMENT '内部编制组织ID',
    compiler_org_name VARCHAR(200) NOT NULL COMMENT '编制单位名称快照',
    prepared_date DATE NULL COMMENT '编制日期',
    base_date DATE NOT NULL COMMENT '测算基准日',
    currency_code CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    market_analysis TEXT NULL,
    technical_analysis TEXT NULL,
    financial_analysis TEXT NULL,
    risk_analysis TEXT NULL,
    total_investment DECIMAL(18,2) NOT NULL DEFAULT 0,
    own_capital DECIMAL(18,2) NOT NULL DEFAULT 0,
    financing_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    construction_period_months INT NULL,
    operation_period_months INT NULL,
    annual_revenue DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_net_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_present_value DECIMAL(18,2) NULL,
    roi DECIMAL(9,4) NULL,
    irr DECIMAL(9,4) NULL,
    payback_period DECIMAL(9,2) NULL,
    discount_rate DECIMAL(9,4) NULL,
    calculation_assumption TEXT NULL,
    risk_conclusion VARCHAR(30) NOT NULL DEFAULT 'ACCEPTABLE',
    conclusion VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    conclusion_summary VARCHAR(2000) NULL,
    approval_instance_ref VARCHAR(100) NULL,
    primary_file_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content_hash VARCHAR(128) NULL,
    frozen_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_feasibility_version (feasibility_id, version_no, delete_token),
    KEY idx_inv_feasibility_version_status (feasibility_id, status, deleted),
    KEY idx_inv_feasibility_approval (approval_instance_ref, deleted),
    CONSTRAINT fk_inv_feasibility_version_header FOREIGN KEY (feasibility_id)
        REFERENCES investment_feasibility(id),
    CONSTRAINT fk_inv_feasibility_version_org FOREIGN KEY (compiler_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT fk_inv_feasibility_version_file FOREIGN KEY (primary_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_feasibility_version_no CHECK (version_no > 0),
    CONSTRAINT chk_inv_feasibility_version_amount CHECK (
        total_investment >= 0 AND own_capital >= 0 AND financing_amount >= 0
        AND annual_revenue >= 0 AND annual_cost >= 0 AND annual_tax >= 0
    ),
    CONSTRAINT chk_inv_feasibility_version_period CHECK (
        (construction_period_months IS NULL OR construction_period_months >= 0)
        AND (operation_period_months IS NULL OR operation_period_months >= 0)
        AND (payback_period IS NULL OR payback_period >= 0)
    ),
    CONSTRAINT chk_inv_feasibility_version_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资可研报告版本表';

ALTER TABLE investment_feasibility
    ADD CONSTRAINT fk_inv_feasibility_current_version FOREIGN KEY (current_version_id)
        REFERENCES investment_feasibility_version(id),
    ADD CONSTRAINT fk_inv_feasibility_frozen_version FOREIGN KEY (current_frozen_version_id)
        REFERENCES investment_feasibility_version(id);

CREATE TABLE investment_due_diligence_package (
    id BIGINT NOT NULL COMMENT '尽调包ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    package_version INT NOT NULL COMMENT '尽调包版本',
    rule_version VARCHAR(50) NOT NULL COMMENT '尽调规则版本',
    required_types VARCHAR(200) NOT NULL COMMENT '必需尽调类型快照',
    overall_conclusion VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    open_blocking_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    frozen_time DATETIME(3) NULL,
    content_hash VARCHAR(128) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_dd_package_version (investment_id, package_version, delete_token),
    KEY idx_inv_dd_package_status (investment_id, status, deleted),
    KEY idx_inv_dd_package_blocking (status, open_blocking_count, deleted),
    CONSTRAINT fk_inv_dd_package_investment FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_inv_dd_package_version CHECK (package_version > 0),
    CONSTRAINT chk_inv_dd_package_count CHECK (open_blocking_count >= 0),
    CONSTRAINT chk_inv_dd_package_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资尽调包表';

CREATE TABLE investment_due_diligence (
    id BIGINT NOT NULL COMMENT '尽调报告版本ID',
    package_id BIGINT NOT NULL COMMENT '尽调包ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    due_diligence_type VARCHAR(30) NOT NULL COMMENT '尽调类型',
    report_version INT NOT NULL COMMENT '报告版本',
    report_no VARCHAR(100) NULL,
    report_name VARCHAR(200) NOT NULL,
    entrusted_org VARCHAR(200) NULL,
    lead_person_id BIGINT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    base_date DATE NULL,
    scope_summary VARCHAR(2000) NULL,
    methodology_summary VARCHAR(2000) NULL,
    conclusion VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    conclusion_summary VARCHAR(2000) NULL,
    material_risk_count INT NOT NULL DEFAULT 0,
    unresolved_risk_count INT NOT NULL DEFAULT 0,
    approval_instance_ref VARCHAR(100) NULL,
    primary_file_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content_hash VARCHAR(128) NULL,
    frozen_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_dd_report_version
        (package_id, due_diligence_type, report_version, delete_token),
    KEY idx_inv_dd_investment (investment_id, due_diligence_type, status, deleted),
    KEY idx_inv_dd_package_read (package_id, status, deleted),
    KEY idx_inv_dd_approval (approval_instance_ref, deleted),
    CONSTRAINT fk_inv_dd_package FOREIGN KEY (package_id)
        REFERENCES investment_due_diligence_package(id),
    CONSTRAINT fk_inv_dd_investment FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT fk_inv_dd_file FOREIGN KEY (primary_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_dd_report_version CHECK (report_version > 0),
    CONSTRAINT chk_inv_dd_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_inv_dd_counts CHECK (material_risk_count >= 0 AND unresolved_risk_count >= 0),
    CONSTRAINT chk_inv_dd_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资尽职调查报告版本表';

CREATE TABLE investment_due_diligence_item (
    id BIGINT NOT NULL COMMENT '尽调问题ID',
    due_diligence_id BIGINT NOT NULL COMMENT '尽调报告ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    item_no VARCHAR(50) NOT NULL COMMENT '问题编号',
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    blocking_flag SMALLINT NOT NULL DEFAULT 0,
    problem_description TEXT NOT NULL,
    impact_description TEXT NULL,
    rectification_measure TEXT NULL,
    responsible_org_id BIGINT NULL,
    responsible_person_id BIGINT NULL,
    deadline DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    resolution_summary VARCHAR(2000) NULL,
    evidence_file_id BIGINT NULL,
    reviewer_id BIGINT NULL,
    review_result VARCHAR(20) NULL,
    review_opinion VARCHAR(2000) NULL,
    reviewed_time DATETIME(3) NULL,
    risk_acceptance_ref VARCHAR(100) NULL,
    closed_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_dd_item_no (due_diligence_id, item_no, delete_token),
    KEY idx_inv_dd_item_investment (investment_id, severity, status, deleted),
    KEY idx_inv_dd_item_responsible (responsible_person_id, status, deadline, deleted),
    KEY idx_inv_dd_item_status (due_diligence_id, status, deleted),
    CONSTRAINT fk_inv_dd_item_report FOREIGN KEY (due_diligence_id)
        REFERENCES investment_due_diligence(id),
    CONSTRAINT fk_inv_dd_item_investment FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT fk_inv_dd_item_org FOREIGN KEY (responsible_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT fk_inv_dd_item_file FOREIGN KEY (evidence_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_dd_item_blocking CHECK (blocking_flag IN (0, 1)),
    CONSTRAINT chk_inv_dd_item_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='尽调问题与整改表';

CREATE TABLE investment_scheme (
    id BIGINT NOT NULL COMMENT '投资方案档案ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    current_version_id BIGINT NULL COMMENT '当前工作版本ID',
    current_frozen_version_id BIGINT NULL COMMENT '当前冻结版本ID',
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_scheme_investment (investment_id, delete_token),
    KEY idx_inv_scheme_status (investment_id, status, deleted),
    CONSTRAINT fk_inv_scheme_investment FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_inv_scheme_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资方案档案表';

CREATE TABLE investment_scheme_version (
    id BIGINT NOT NULL COMMENT '投资方案版本ID',
    scheme_id BIGINT NOT NULL COMMENT '方案档案ID',
    version_no INT NOT NULL COMMENT '版本号',
    scheme_no VARCHAR(100) NULL COMMENT '方案编号',
    scheme_name VARCHAR(200) NOT NULL COMMENT '方案名称',
    investment_subject_org_id BIGINT NOT NULL COMMENT '投资主体组织ID',
    investee_company_id BIGINT NULL COMMENT '被投企业ID',
    investment_type VARCHAR(50) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'CNY',
    investment_method VARCHAR(50) NOT NULL,
    contribution_schedule_summary VARCHAR(2000) NULL,
    pre_investment_ratio DECIMAL(7,4) NULL,
    post_investment_ratio DECIMAL(7,4) NULL,
    share_type VARCHAR(50) NULL,
    control_type VARCHAR(30) NULL,
    governance_arrangement TEXT NULL,
    cooperation_mode VARCHAR(50) NULL,
    partner_arrangement TEXT NULL,
    valuation_amount DECIMAL(18,2) NULL,
    valuation_base_date DATE NULL,
    income_distribution TEXT NULL,
    exit_type VARCHAR(50) NULL,
    exit_plan TEXT NULL,
    conditions_precedent TEXT NULL,
    feasibility_version_id BIGINT NOT NULL,
    due_diligence_package_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content_hash VARCHAR(128) NULL,
    frozen_time DATETIME(3) NULL,
    primary_file_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_scheme_version (scheme_id, version_no, delete_token),
    KEY idx_inv_scheme_version_status (scheme_id, status, deleted),
    KEY idx_inv_scheme_feasibility (feasibility_version_id, deleted),
    KEY idx_inv_scheme_dd_package (due_diligence_package_id, deleted),
    KEY idx_inv_scheme_investee (investee_company_id, status, deleted),
    CONSTRAINT fk_inv_scheme_version_header FOREIGN KEY (scheme_id)
        REFERENCES investment_scheme(id),
    CONSTRAINT fk_inv_scheme_version_org FOREIGN KEY (investment_subject_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT fk_inv_scheme_version_investee FOREIGN KEY (investee_company_id)
        REFERENCES investment_company(id),
    CONSTRAINT fk_inv_scheme_version_feasibility FOREIGN KEY (feasibility_version_id)
        REFERENCES investment_feasibility_version(id),
    CONSTRAINT fk_inv_scheme_version_dd FOREIGN KEY (due_diligence_package_id)
        REFERENCES investment_due_diligence_package(id),
    CONSTRAINT fk_inv_scheme_version_file FOREIGN KEY (primary_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_scheme_version_no CHECK (version_no > 0),
    CONSTRAINT chk_inv_scheme_version_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_inv_scheme_version_ratio CHECK (
        (pre_investment_ratio IS NULL OR pre_investment_ratio BETWEEN 0 AND 100)
        AND (post_investment_ratio IS NULL OR post_investment_ratio BETWEEN 0 AND 100)
    ),
    CONSTRAINT chk_inv_scheme_version_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资方案版本表';

ALTER TABLE investment_scheme
    ADD CONSTRAINT fk_inv_scheme_current_version FOREIGN KEY (current_version_id)
        REFERENCES investment_scheme_version(id),
    ADD CONSTRAINT fk_inv_scheme_frozen_version FOREIGN KEY (current_frozen_version_id)
        REFERENCES investment_scheme_version(id);

CREATE TABLE investment_scheme_funding (
    id BIGINT NOT NULL COMMENT '方案资金来源ID',
    scheme_version_id BIGINT NOT NULL COMMENT '方案版本ID',
    funding_type VARCHAR(30) NOT NULL COMMENT '资金来源类型',
    provider_name VARCHAR(200) NULL COMMENT '资金提供方快照',
    amount DECIMAL(18,2) NOT NULL,
    cost_rate DECIMAL(9,4) NULL,
    available_date DATE NULL,
    confirmed_flag SMALLINT NOT NULL DEFAULT 0,
    evidence_file_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_scheme_funding
        (scheme_version_id, funding_type, provider_name, delete_token),
    KEY idx_inv_scheme_funding_confirmed (scheme_version_id, confirmed_flag, deleted),
    CONSTRAINT fk_inv_scheme_funding_version FOREIGN KEY (scheme_version_id)
        REFERENCES investment_scheme_version(id),
    CONSTRAINT fk_inv_scheme_funding_file FOREIGN KEY (evidence_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_scheme_funding_amount CHECK (amount >= 0),
    CONSTRAINT chk_inv_scheme_funding_confirmed CHECK (confirmed_flag IN (0, 1)),
    CONSTRAINT chk_inv_scheme_funding_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资方案资金来源表';

ALTER TABLE investment_decision
    ADD COLUMN decision_no VARCHAR(100) NULL COMMENT '决策事项编号' AFTER investment_id,
    ADD COLUMN decision_subject VARCHAR(300) NULL COMMENT '决策事项' AFTER decision_no,
    ADD COLUMN scheme_version_id BIGINT NULL COMMENT '冻结方案版本ID' AFTER decision_subject,
    ADD COLUMN scheme_content_hash VARCHAR(128) NULL COMMENT '方案哈希快照' AFTER scheme_version_id,
    ADD COLUMN feasibility_version_id BIGINT NULL COMMENT '冻结可研版本ID' AFTER scheme_content_hash,
    ADD COLUMN due_diligence_package_id BIGINT NULL COMMENT '通过的尽调包ID' AFTER feasibility_version_id,
    ADD COLUMN decision_package_version INT NOT NULL DEFAULT 1 AFTER due_diligence_package_id,
    ADD COLUMN route_rule_version VARCHAR(50) NULL AFTER decision_package_version,
    ADD COLUMN route_snapshot_hash VARCHAR(128) NULL AFTER route_rule_version,
    ADD COLUMN major_decision_applicable SMALLINT NOT NULL DEFAULT 0 AFTER route_snapshot_hash,
    ADD COLUMN major_decision_ref VARCHAR(100) NULL AFTER major_decision_applicable,
    ADD COLUMN party_pre_study_required SMALLINT NOT NULL DEFAULT 0 AFTER major_decision_ref,
    ADD COLUMN approval_status VARCHAR(30) NULL AFTER party_pre_study_required,
    ADD COLUMN decision_summary VARCHAR(2000) NULL AFTER decision_result,
    ADD COLUMN submitted_time DATETIME(3) NULL AFTER decision_summary,
    ADD COLUMN completed_time DATETIME(3) NULL AFTER submitted_time,
    ADD COLUMN supersedes_decision_id BIGINT NULL AFTER completed_time;

UPDATE investment_decision
SET decision_no = CONCAT('LEGACY-DEC-', id),
    decision_subject = COALESCE(meeting_name, decision_type, CONCAT('历史决策-', id)),
    approval_status = CASE
        WHEN decision_result IS NULL THEN 'MATERIAL_REVIEW'
        ELSE 'COMPLETED'
    END,
    decision_summary = decision_content,
    completed_time = CASE WHEN decision_result IS NULL THEN NULL ELSE create_time END
WHERE decision_no IS NULL;

ALTER TABLE investment_decision
    MODIFY COLUMN decision_no VARCHAR(100) NOT NULL COMMENT '决策事项编号',
    MODIFY COLUMN decision_subject VARCHAR(300) NOT NULL COMMENT '决策事项',
    MODIFY COLUMN approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD UNIQUE KEY uk_inv_decision_no (decision_no, delete_token),
    ADD KEY idx_inv_decision_status (investment_id, approval_status, deleted),
    ADD KEY idx_inv_decision_scheme (scheme_version_id, deleted),
    ADD KEY idx_inv_decision_major (major_decision_ref, deleted),
    ADD KEY idx_inv_decision_completed (decision_result, completed_time, deleted),
    ADD CONSTRAINT fk_inv_decision_scheme FOREIGN KEY (scheme_version_id)
        REFERENCES investment_scheme_version(id),
    ADD CONSTRAINT fk_inv_decision_feasibility FOREIGN KEY (feasibility_version_id)
        REFERENCES investment_feasibility_version(id),
    ADD CONSTRAINT fk_inv_decision_dd_package FOREIGN KEY (due_diligence_package_id)
        REFERENCES investment_due_diligence_package(id),
    ADD CONSTRAINT fk_inv_decision_supersedes FOREIGN KEY (supersedes_decision_id)
        REFERENCES investment_decision(id),
    ADD CONSTRAINT chk_inv_decision_flags CHECK (
        major_decision_applicable IN (0, 1) AND party_pre_study_required IN (0, 1)
    );

CREATE TABLE investment_decision_node (
    id BIGINT NOT NULL COMMENT '决策节点ID',
    decision_id BIGINT NOT NULL COMMENT '决策事项ID',
    node_code VARCHAR(50) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    sequence_no INT NOT NULL,
    required_flag SMALLINT NOT NULL DEFAULT 1,
    veto_flag SMALLINT NOT NULL DEFAULT 0,
    decision_body VARCHAR(200) NULL,
    authority_basis VARCHAR(1000) NULL,
    approval_instance_ref VARCHAR(100) NULL,
    meeting_ref VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    result VARCHAR(30) NULL,
    opinion_summary VARCHAR(2000) NULL,
    started_time DATETIME(3) NULL,
    decided_time DATETIME(3) NULL,
    primary_file_id BIGINT NULL,
    correction_of_node_id BIGINT NULL,
    record_hash VARCHAR(128) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_decision_node (decision_id, node_code, sequence_no, delete_token),
    KEY idx_inv_decision_node_status (decision_id, status, sequence_no, deleted),
    KEY idx_inv_decision_node_approval (approval_instance_ref, deleted),
    KEY idx_inv_decision_node_meeting (meeting_ref, deleted),
    CONSTRAINT fk_inv_decision_node_decision FOREIGN KEY (decision_id)
        REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_decision_node_file FOREIGN KEY (primary_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT fk_inv_decision_node_correction FOREIGN KEY (correction_of_node_id)
        REFERENCES investment_decision_node(id),
    CONSTRAINT chk_inv_decision_node_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_inv_decision_node_flags CHECK (required_flag IN (0, 1) AND veto_flag IN (0, 1)),
    CONSTRAINT chk_inv_decision_node_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策节点表';

CREATE TABLE investment_decision_condition (
    id BIGINT NOT NULL COMMENT '附条件任务ID',
    decision_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    condition_no VARCHAR(50) NOT NULL,
    condition_type VARCHAR(30) NOT NULL,
    condition_content TEXT NOT NULL,
    blocking_flag SMALLINT NOT NULL DEFAULT 1,
    responsible_org_id BIGINT NOT NULL,
    responsible_person_id BIGINT NOT NULL,
    deadline DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    rectification_summary VARCHAR(2000) NULL,
    evidence_file_id BIGINT NULL,
    submitted_time DATETIME(3) NULL,
    reviewer_id BIGINT NULL,
    review_result VARCHAR(20) NULL,
    review_opinion VARCHAR(2000) NULL,
    reviewed_time DATETIME(3) NULL,
    waiver_approval_ref VARCHAR(100) NULL,
    waiver_expire_date DATE NULL,
    closed_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_decision_condition (decision_id, condition_no, delete_token),
    KEY idx_inv_condition_responsible (responsible_person_id, status, deadline, deleted),
    KEY idx_inv_condition_blocking (decision_id, blocking_flag, status, deleted),
    KEY idx_inv_condition_source (source_node_id, status, deleted),
    CONSTRAINT fk_inv_condition_decision FOREIGN KEY (decision_id)
        REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_condition_source FOREIGN KEY (source_node_id)
        REFERENCES investment_decision_node(id),
    CONSTRAINT fk_inv_condition_org FOREIGN KEY (responsible_org_id)
        REFERENCES sys_org(id),
    CONSTRAINT fk_inv_condition_file FOREIGN KEY (evidence_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_condition_blocking CHECK (blocking_flag IN (0, 1)),
    CONSTRAINT chk_inv_condition_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策附加条件表';

CREATE TABLE investment_decision_condition_action (
    id BIGINT NOT NULL COMMENT '条件动作ID',
    condition_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_role_snapshot VARCHAR(200) NULL,
    action_opinion VARCHAR(2000) NULL,
    evidence_file_id BIGINT NULL,
    action_time DATETIME(3) NOT NULL,
    trace_id VARCHAR(64) NULL,
    idempotency_key VARCHAR(100) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_condition_action_idempotency (condition_id, idempotency_key, delete_token),
    KEY idx_inv_condition_action_time (condition_id, action_time, deleted),
    KEY idx_inv_condition_action_operator (operator_id, action_time, deleted),
    CONSTRAINT fk_inv_condition_action_condition FOREIGN KEY (condition_id)
        REFERENCES investment_decision_condition(id),
    CONSTRAINT fk_inv_condition_action_file FOREIGN KEY (evidence_file_id)
        REFERENCES sys_file(id),
    CONSTRAINT chk_inv_condition_action_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策条件动作历史表';
