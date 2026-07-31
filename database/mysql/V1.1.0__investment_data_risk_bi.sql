-- 国企数字化治理与经营赋能平台 V1.0 第二批业务域
-- 投资管理、数据资产、风险合规、驾驶舱分析

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE investment_plan (
    id BIGINT NOT NULL,
    plan_year INT NOT NULL,
    plan_name VARCHAR(200) NOT NULL,
    industry_direction VARCHAR(100) NOT NULL,
    plan_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    responsible_dept BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_plan_name (plan_year, plan_name, delete_token),
    KEY idx_investment_plan_dept (responsible_dept, plan_year, status, deleted),
    CONSTRAINT fk_investment_plan_dept FOREIGN KEY (responsible_dept) REFERENCES sys_org(id),
    CONSTRAINT chk_investment_plan_amount CHECK (plan_amount >= 0 AND actual_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资年度计划';

CREATE TABLE investment_project (
    id BIGINT NOT NULL,
    investment_no VARCHAR(64) NOT NULL,
    plan_id BIGINT NULL,
    project_id BIGINT NULL,
    investment_name VARCHAR(200) NOT NULL,
    investment_type VARCHAR(32) NOT NULL COMMENT 'EQUITY/FIXED_ASSET/FUND/OTHER',
    investment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    investment_ratio DECIMAL(7,4) NULL COMMENT '百分比，取值0-100',
    partner_name VARCHAR(200) NULL,
    expected_return DECIMAL(18,2) NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_project_no (investment_no, delete_token),
    KEY idx_investment_project_plan (plan_id, approval_status, deleted),
    KEY idx_investment_project_project (project_id, deleted),
    KEY idx_investment_project_approval (approval_status, risk_level, deleted),
    CONSTRAINT fk_investment_project_plan FOREIGN KEY (plan_id) REFERENCES investment_plan(id),
    CONSTRAINT fk_investment_project_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_investment_project_amount CHECK (
        investment_amount >= 0 AND expected_return >= 0
    ),
    CONSTRAINT chk_investment_project_ratio CHECK (
        investment_ratio IS NULL OR (investment_ratio >= 0 AND investment_ratio <= 100)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资事项';

CREATE TABLE investment_decision (
    id BIGINT NOT NULL,
    investment_id BIGINT NOT NULL,
    decision_type VARCHAR(32) NOT NULL COMMENT 'PARTY_COMMITTEE/MANAGER_MEETING/BOARD/SHAREHOLDER',
    meeting_type VARCHAR(64) NULL,
    meeting_date DATE NOT NULL,
    decision_result VARCHAR(32) NOT NULL,
    decision_file VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_decision_item (investment_id, meeting_date, deleted),
    KEY idx_investment_decision_result (decision_result, meeting_date, deleted),
    CONSTRAINT fk_investment_decision_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策记录';

CREATE TABLE investment_evaluation (
    id BIGINT NOT NULL,
    investment_id BIGINT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    annual_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    roi DECIMAL(9,4) NULL COMMENT '投资回报率百分比',
    irr DECIMAL(9,4) NULL COMMENT '内部收益率百分比',
    payback_period DECIMAL(9,2) NULL COMMENT '投资回收期，单位年',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_evaluation_item (investment_id, delete_token),
    CONSTRAINT fk_investment_evaluation_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_evaluation_amount CHECK (
        total_amount >= 0 AND annual_income >= 0 AND annual_cost >= 0
    ),
    CONSTRAINT chk_investment_evaluation_payback CHECK (
        payback_period IS NULL OR payback_period >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资经济测算';

CREATE TABLE investment_company (
    id BIGINT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    credit_code VARCHAR(32) NOT NULL,
    register_capital DECIMAL(18,2) NOT NULL DEFAULT 0,
    legal_person VARCHAR(64) NULL,
    establish_date DATE NULL,
    industry VARCHAR(100) NULL,
    company_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_company_credit (credit_code, delete_token),
    KEY idx_investment_company_name (company_name, deleted),
    KEY idx_investment_company_status (company_status, industry, deleted),
    CONSTRAINT chk_investment_company_capital CHECK (register_capital >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='被投资企业';

CREATE TABLE investment_equity (
    id BIGINT NOT NULL,
    investment_company_id BIGINT NOT NULL,
    holder_name VARCHAR(200) NOT NULL,
    holding_ratio DECIMAL(7,4) NOT NULL,
    investment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    share_type VARCHAR(32) NULL,
    acquire_date DATE NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_equity_holder (
        investment_company_id, holder_name, share_type, delete_token
    ),
    KEY idx_investment_equity_company (investment_company_id, deleted),
    CONSTRAINT fk_investment_equity_company FOREIGN KEY (investment_company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_equity_ratio CHECK (holding_ratio >= 0 AND holding_ratio <= 100),
    CONSTRAINT chk_investment_equity_amount CHECK (investment_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股权台账';

CREATE TABLE investment_shareholder_right (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    meeting_type VARCHAR(32) NOT NULL COMMENT 'SHAREHOLDER/BOARD/SUPERVISOR',
    meeting_date DATE NOT NULL,
    agenda TEXT NULL,
    resolution TEXT NULL,
    file_url VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_right_company (company_id, meeting_date, meeting_type, deleted),
    CONSTRAINT fk_investment_right_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股东权利管理';

CREATE TABLE investment_director (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    person_name VARCHAR(64) NOT NULL,
    director_position VARCHAR(64) NOT NULL,
    appoint_date DATE NULL,
    term_start DATE NULL,
    term_end DATE NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_director_company (company_id, director_position, deleted),
    KEY idx_investment_director_term (term_end, deleted),
    CONSTRAINT fk_investment_director_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_director_term CHECK (
        term_start IS NULL OR term_end IS NULL OR term_start <= term_end
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='董事监事管理';

CREATE TABLE investment_monitor_indicator (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    indicator_name VARCHAR(128) NOT NULL,
    target_value DECIMAL(18,4) NULL,
    actual_value DECIMAL(18,4) NULL,
    monitor_period VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_monitor_period (
        company_id, indicator_name, monitor_period, delete_token
    ),
    KEY idx_investment_monitor_status (status, monitor_period, deleted),
    CONSTRAINT fk_investment_monitor_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投后监管指标';

CREATE TABLE investment_income (
    id BIGINT NOT NULL,
    investment_id BIGINT NOT NULL,
    income_type VARCHAR(32) NOT NULL COMMENT 'DIVIDEND/EQUITY_APPRECIATION/EXIT',
    income_date DATE NOT NULL,
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_income_item (investment_id, income_date, deleted),
    KEY idx_investment_income_type (income_type, income_date, deleted),
    CONSTRAINT fk_investment_income_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_income_amount CHECK (income_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资收益记录';

CREATE TABLE investment_risk (
    id BIGINT NOT NULL,
    investment_id BIGINT NOT NULL,
    risk_type VARCHAR(32) NOT NULL,
    risk_description VARCHAR(1000) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    response_measure VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_risk_item (investment_id, status, deleted),
    KEY idx_investment_risk_level (risk_level, status, deleted),
    CONSTRAINT fk_investment_risk_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资风险';

CREATE TABLE investment_exit (
    id BIGINT NOT NULL,
    investment_id BIGINT NOT NULL,
    exit_type VARCHAR(32) NOT NULL COMMENT 'TRANSFER/REPURCHASE/LIQUIDATION/IPO',
    exit_date DATE NULL,
    exit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    exit_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_exit_item (investment_id, status, exit_date, deleted),
    CONSTRAINT fk_investment_exit_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_exit_amount CHECK (exit_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资退出管理';

CREATE TABLE data_resource (
    id BIGINT NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    resource_name VARCHAR(200) NOT NULL,
    resource_type VARCHAR(32) NOT NULL COMMENT 'PUBLIC/ENTERPRISE/PROJECT/BUSINESS',
    source_unit BIGINT NOT NULL,
    responsible_person BIGINT NOT NULL,
    update_frequency VARCHAR(32) NULL,
    data_size BIGINT NOT NULL DEFAULT 0 COMMENT '数据量，单位字节',
    security_level VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_resource_code (resource_code, delete_token),
    KEY idx_data_resource_source (source_unit, resource_type, deleted),
    KEY idx_data_resource_security (security_level, status, deleted),
    CONSTRAINT fk_data_resource_source FOREIGN KEY (source_unit) REFERENCES sys_org(id),
    CONSTRAINT fk_data_resource_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_data_resource_size CHECK (data_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资源目录';

CREATE TABLE data_asset (
    id BIGINT NOT NULL,
    asset_code VARCHAR(64) NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    resource_id BIGINT NOT NULL,
    ownership VARCHAR(200) NULL,
    application_scene VARCHAR(500) NULL,
    value_level VARCHAR(32) NULL,
    evaluation_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_asset_code (asset_code, delete_token),
    KEY idx_data_asset_resource (resource_id, status, deleted),
    KEY idx_data_asset_value (value_level, status, deleted),
    CONSTRAINT fk_data_asset_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT chk_data_asset_amount CHECK (evaluation_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资产';

CREATE TABLE data_product (
    id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    asset_id BIGINT NOT NULL,
    service_object VARCHAR(200) NULL,
    service_mode VARCHAR(32) NOT NULL,
    price DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_product_code (product_code, delete_token),
    KEY idx_data_product_asset (asset_id, status, deleted),
    CONSTRAINT fk_data_product_asset FOREIGN KEY (asset_id) REFERENCES data_asset(id),
    CONSTRAINT chk_data_product_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据产品';

CREATE TABLE data_authorization (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    customer VARCHAR(200) NOT NULL,
    authorization_type VARCHAR(32) NOT NULL COMMENT 'SHARE/SERVICE/TRADE/LICENSE',
    start_date DATE NOT NULL,
    end_date DATE NULL,
    purpose VARCHAR(500) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_authorization_product (product_id, approval_status, deleted),
    KEY idx_data_authorization_expiry (end_date, approval_status, deleted),
    CONSTRAINT fk_data_authorization_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT chk_data_authorization_dates CHECK (end_date IS NULL OR start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据授权记录';

CREATE TABLE data_income (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    income_date DATE NOT NULL,
    customer VARCHAR(200) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_income_product (product_id, income_date, deleted),
    KEY idx_data_income_contract (contract_id, deleted),
    CONSTRAINT fk_data_income_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT fk_data_income_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT chk_data_income_amount CHECK (income_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据收益';

CREATE TABLE data_quality (
    id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    completeness_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    accuracy_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    timeliness_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    quality_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    evaluation_period VARCHAR(32) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_quality_period (resource_id, evaluation_period, delete_token),
    KEY idx_data_quality_score (quality_score, evaluation_period, deleted),
    CONSTRAINT fk_data_quality_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT chk_data_quality_scores CHECK (
        completeness_score BETWEEN 0 AND 100
        AND accuracy_score BETWEEN 0 AND 100
        AND timeliness_score BETWEEN 0 AND 100
        AND quality_score BETWEEN 0 AND 100
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量评价';

CREATE TABLE data_access_log (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    access_time DATETIME(3) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    ip VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_access_user_time (user_id, access_time),
    KEY idx_data_access_resource_time (resource_id, access_time),
    KEY idx_data_access_result_time (result, access_time),
    KEY idx_data_access_trace (trace_id),
    CONSTRAINT fk_data_access_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_data_access_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据使用审计日志';

CREATE TABLE risk_info (
    id BIGINT NOT NULL,
    risk_code VARCHAR(64) NOT NULL,
    risk_name VARCHAR(200) NOT NULL,
    risk_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    responsible_dept BIGINT NOT NULL,
    responsible_person BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_info_code (risk_code, delete_token),
    KEY idx_risk_info_dept (responsible_dept, status, deleted),
    KEY idx_risk_info_level (risk_level, risk_type, status, deleted),
    CONSTRAINT fk_risk_info_dept FOREIGN KEY (responsible_dept) REFERENCES sys_org(id),
    CONSTRAINT fk_risk_info_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险库';

CREATE TABLE risk_rule (
    id BIGINT NOT NULL,
    rule_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    rule_condition VARCHAR(1000) NOT NULL COMMENT '规则表达式，禁止存储可执行SQL',
    warning_level VARCHAR(16) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_rule_business (business_type, enabled, deleted),
    KEY idx_risk_rule_level (warning_level, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险预警规则';

CREATE TABLE risk_rectification (
    id BIGINT NOT NULL,
    risk_id BIGINT NOT NULL,
    problem VARCHAR(1000) NOT NULL,
    measure VARCHAR(1000) NOT NULL,
    responsible_person BIGINT NOT NULL,
    deadline DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    completion_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_rectification_risk (risk_id, status, deleted),
    KEY idx_risk_rectification_deadline (deadline, status, deleted),
    CONSTRAINT fk_risk_rectification_risk FOREIGN KEY (risk_id) REFERENCES risk_info(id),
    CONSTRAINT fk_risk_rectification_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险整改任务';

CREATE TABLE audit_problem (
    id BIGINT NOT NULL,
    audit_project VARCHAR(200) NOT NULL,
    problem_content VARCHAR(2000) NOT NULL,
    problem_level VARCHAR(16) NOT NULL,
    department BIGINT NOT NULL,
    rectification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_audit_problem_dept (department, rectification_status, deleted),
    KEY idx_audit_problem_level (problem_level, rectification_status, deleted),
    CONSTRAINT fk_audit_problem_dept FOREIGN KEY (department) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计问题';

CREATE TABLE inspection_problem (
    id BIGINT NOT NULL,
    inspection_batch VARCHAR(100) NOT NULL,
    problem VARCHAR(2000) NOT NULL,
    responsible_unit BIGINT NOT NULL,
    deadline DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_inspection_problem_unit (responsible_unit, status, deleted),
    KEY idx_inspection_problem_deadline (deadline, status, deleted),
    CONSTRAINT fk_inspection_problem_unit FOREIGN KEY (responsible_unit) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡察问题';

-- BI 表保存业务数据的同步快照，不建立到在线交易表的物理外键。
CREATE TABLE bi_operation_dashboard (
    id BIGINT NOT NULL,
    statistic_date DATE NOT NULL,
    income DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    asset DECIMAL(18,2) NOT NULL DEFAULT 0,
    cash DECIMAL(18,2) NOT NULL DEFAULT 0,
    project_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_operation_date (statistic_date, delete_token),
    KEY idx_bi_operation_create (create_time),
    CONSTRAINT chk_bi_operation_count CHECK (project_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营指标宽表';

CREATE TABLE bi_project_analysis (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL COMMENT '项目逻辑引用，不设物理外键',
    statistic_date DATE NOT NULL,
    income DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL,
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_project_date (project_id, statistic_date, delete_token),
    KEY idx_bi_project_risk (risk_level, statistic_date, deleted),
    KEY idx_bi_project_progress (progress, statistic_date, deleted),
    CONSTRAINT chk_bi_project_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目分析宽表';

-- 党建表先于项目、投资表创建，跨模块外键在依赖表就绪后补充。
ALTER TABLE party_project
    ADD CONSTRAINT fk_party_project_business_project
    FOREIGN KEY (project_id) REFERENCES project_info(id);

ALTER TABLE party_major_decision
    ADD CONSTRAINT fk_party_major_decision_investment
    FOREIGN KEY (investment_id) REFERENCES investment_project(id);

SET FOREIGN_KEY_CHECKS = 1;
