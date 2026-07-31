-- ============================================
-- 国企数字化治理与经营赋能平台
-- 投资管理数据库完整 SQL
-- 战略计划 -> 投资立项 -> 决策支付 -> 股权形成 -> 投后监管 -> 收益退出
-- 主键由应用雪花算法生成，兼顾 MySQL、达梦和人大金仓迁移
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE investment_plan (
    id BIGINT NOT NULL COMMENT '投资计划ID',
    plan_year INT NOT NULL COMMENT '计划年度',
    plan_name VARCHAR(200) NOT NULL COMMENT '计划名称',
    investment_direction VARCHAR(200) NOT NULL COMMENT '投资方向',
    industry_type VARCHAR(100) NULL COMMENT '产业领域',
    plan_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '计划投资金额',
    actual_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际投资金额',
    responsible_org BIGINT NOT NULL COMMENT '责任组织ID',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
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
    KEY idx_investment_plan_org (responsible_org, plan_year, status, deleted),
    KEY idx_investment_plan_status (plan_year, status, deleted),
    CONSTRAINT fk_investment_plan_org FOREIGN KEY (responsible_org) REFERENCES sys_org(id),
    CONSTRAINT chk_investment_plan_year CHECK (plan_year BETWEEN 1900 AND 9999),
    CONSTRAINT chk_investment_plan_amount CHECK (plan_amount >= 0 AND actual_amount >= 0),
    CONSTRAINT chk_investment_plan_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度投资计划表';

CREATE TABLE investment_company (
    id BIGINT NOT NULL COMMENT '被投资企业ID',
    company_name VARCHAR(200) NOT NULL COMMENT '企业名称',
    credit_code VARCHAR(100) NOT NULL COMMENT '统一社会信用代码',
    legal_person VARCHAR(50) NULL COMMENT '法定代表人',
    register_capital DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '注册资本',
    establish_date DATE NULL COMMENT '成立日期',
    industry VARCHAR(100) NULL COMMENT '所属行业',
    company_type VARCHAR(50) NULL COMMENT '参股/控股等企业类型',
    registered_address VARCHAR(300) NULL COMMENT '注册地址',
    business_scope TEXT NULL COMMENT '经营范围',
    status VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
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
    KEY idx_investment_company_status (status, industry, deleted),
    CONSTRAINT chk_investment_company_capital CHECK (register_capital >= 0),
    CONSTRAINT chk_investment_company_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='被投资企业信息表';

CREATE TABLE investment_project (
    id BIGINT NOT NULL COMMENT '投资事项ID',
    investment_no VARCHAR(50) NOT NULL COMMENT '投资编号',
    plan_id BIGINT NULL COMMENT '年度投资计划ID',
    project_id BIGINT NULL COMMENT '关联实施项目ID',
    investment_name VARCHAR(200) NOT NULL COMMENT '投资名称',
    investment_type VARCHAR(50) NOT NULL COMMENT 'EQUITY/FIXED_ASSET/FUND/DATA_ASSET/OTHER',
    industry VARCHAR(100) NULL COMMENT '所属行业',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '投资总额',
    own_capital DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '自有资金',
    financing_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '融资金额',
    investment_ratio DECIMAL(7,4) NULL COMMENT '持股比例，百分比',
    partner_name VARCHAR(200) NULL COMMENT '合作方',
    spv_company_id BIGINT NULL COMMENT '被投资企业或SPV公司ID',
    expected_income DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计收益',
    expected_roi DECIMAL(9,4) NULL COMMENT '预计收益率，百分比',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
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
    KEY idx_investment_project_company (spv_company_id, status, deleted),
    KEY idx_investment_project_approval (approval_status, risk_level, deleted),
    CONSTRAINT fk_investment_project_plan FOREIGN KEY (plan_id) REFERENCES investment_plan(id),
    CONSTRAINT fk_investment_project_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_investment_project_company FOREIGN KEY (spv_company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_project_amount CHECK (
        total_amount >= 0 AND own_capital >= 0 AND financing_amount >= 0
        AND expected_income >= 0
    ),
    CONSTRAINT chk_investment_project_ratio CHECK (
        investment_ratio IS NULL OR investment_ratio BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_investment_project_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资项目主表';

CREATE TABLE investment_feasibility (
    id BIGINT NOT NULL COMMENT '可研测算ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    market_analysis TEXT NULL COMMENT '市场分析',
    technical_analysis TEXT NULL COMMENT '技术分析',
    financial_analysis TEXT NULL COMMENT '财务分析',
    risk_analysis TEXT NULL COMMENT '风险分析',
    investment_period INT NULL COMMENT '投资周期，单位月',
    annual_income DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计年收入',
    annual_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计年成本',
    annual_profit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计年利润',
    roi DECIMAL(9,4) NULL COMMENT '投资回报率，百分比',
    irr DECIMAL(9,4) NULL COMMENT '内部收益率，百分比',
    payback_period DECIMAL(9,2) NULL COMMENT '投资回收期，单位年',
    attachment VARCHAR(500) NULL COMMENT '可研附件地址；正式文件建议关联sys_file',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_feasibility_item (investment_id, delete_token),
    CONSTRAINT fk_investment_feasibility_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_feasibility_amount CHECK (
        annual_income >= 0 AND annual_cost >= 0
        AND annual_profit = annual_income - annual_cost
    ),
    CONSTRAINT chk_investment_feasibility_period CHECK (
        (investment_period IS NULL OR investment_period >= 0)
        AND (payback_period IS NULL OR payback_period >= 0)
    ),
    CONSTRAINT chk_investment_feasibility_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资可研测算表';

CREATE TABLE investment_decision (
    id BIGINT NOT NULL COMMENT '投资决策ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    decision_type VARCHAR(50) NOT NULL COMMENT 'PARTY_COMMITTEE/MANAGER_MEETING/BOARD/SHAREHOLDER',
    meeting_date DATE NOT NULL COMMENT '会议日期',
    meeting_name VARCHAR(200) NULL COMMENT '会议名称',
    decision_result VARCHAR(50) NOT NULL COMMENT '决策结果',
    decision_content TEXT NULL COMMENT '决策内容',
    attachment VARCHAR(500) NULL COMMENT '决策附件地址；正式文件建议关联sys_file',
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
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_decision_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策记录表';

CREATE TABLE investment_payment (
    id BIGINT NOT NULL COMMENT '投资支付ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    payment_date DATE NOT NULL COMMENT '支付日期',
    payment_amount DECIMAL(18,2) NOT NULL COMMENT '支付金额',
    payment_type VARCHAR(50) NOT NULL COMMENT '支付类型',
    bank_account VARCHAR(100) NULL COMMENT '银行账户，敏感字段应加密存储',
    approval_no VARCHAR(100) NULL COMMENT '审批单号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_payment_approval (approval_no, delete_token),
    KEY idx_investment_payment_item (investment_id, payment_date, deleted),
    KEY idx_investment_payment_date (payment_date, deleted),
    CONSTRAINT fk_investment_payment_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_payment_amount CHECK (payment_amount > 0),
    CONSTRAINT chk_investment_payment_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资资金支付记录表';

CREATE TABLE investment_equity (
    id BIGINT NOT NULL COMMENT '股权台账ID',
    company_id BIGINT NOT NULL COMMENT '被投资企业ID',
    holder_name VARCHAR(200) NOT NULL COMMENT '股东名称',
    holder_type VARCHAR(50) NULL COMMENT '股东类型',
    holding_ratio DECIMAL(7,4) NOT NULL COMMENT '持股比例，百分比',
    investment_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '投资金额',
    share_type VARCHAR(50) NULL COMMENT '股份类型',
    acquire_date DATE NULL COMMENT '取得日期',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_equity_holder (company_id, holder_name, share_type, delete_token),
    KEY idx_investment_equity_company (company_id, status, deleted),
    CONSTRAINT fk_investment_equity_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_equity_ratio CHECK (holding_ratio BETWEEN 0 AND 100),
    CONSTRAINT chk_investment_equity_amount CHECK (investment_amount >= 0),
    CONSTRAINT chk_investment_equity_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股权台账表';

CREATE TABLE investment_director (
    id BIGINT NOT NULL COMMENT '董事监事记录ID',
    company_id BIGINT NOT NULL COMMENT '被投资企业ID',
    person_name VARCHAR(50) NOT NULL COMMENT '人员姓名',
    employee_id BIGINT NULL COMMENT '关联员工ID',
    director_position VARCHAR(50) NOT NULL COMMENT '董事/监事等职务，规范原position字段',
    appoint_date DATE NULL COMMENT '委派日期',
    term_start DATE NULL COMMENT '任期开始日期',
    term_end DATE NULL COMMENT '任期结束日期',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_director_company (company_id, director_position, status, deleted),
    KEY idx_investment_director_employee (employee_id, status, deleted),
    KEY idx_investment_director_term (term_end, status, deleted),
    CONSTRAINT fk_investment_director_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id),
    CONSTRAINT fk_investment_director_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_investment_director_term CHECK (
        term_start IS NULL OR term_end IS NULL OR term_start <= term_end
    ),
    CONSTRAINT chk_investment_director_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='董事监事管理表';

CREATE TABLE investment_meeting (
    id BIGINT NOT NULL COMMENT '投资企业会议ID',
    company_id BIGINT NOT NULL COMMENT '被投资企业ID',
    meeting_type VARCHAR(50) NOT NULL COMMENT 'SHAREHOLDER/BOARD/SUPERVISOR',
    meeting_date DATE NOT NULL COMMENT '会议日期',
    agenda TEXT NULL COMMENT '会议议题',
    resolution TEXT NULL COMMENT '会议决议',
    attachment VARCHAR(500) NULL COMMENT '会议附件地址；正式文件建议关联sys_file',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_investment_meeting_company (company_id, meeting_date, meeting_type, deleted),
    CONSTRAINT fk_investment_meeting_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_meeting_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资企业会议记录表';

CREATE TABLE investment_monitor_indicator (
    id BIGINT NOT NULL COMMENT '投后指标ID',
    company_id BIGINT NOT NULL COMMENT '被投资企业ID',
    indicator_name VARCHAR(200) NOT NULL COMMENT '指标名称',
    indicator_type VARCHAR(50) NOT NULL COMMENT '指标类型',
    target_value DECIMAL(18,4) NULL COMMENT '目标值',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    frequency VARCHAR(20) NOT NULL COMMENT '采集频率',
    warning_value DECIMAL(18,4) NULL COMMENT '预警阈值',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_monitor_indicator (company_id, indicator_name, delete_token),
    UNIQUE KEY uk_investment_monitor_indicator_owner (id, company_id),
    KEY idx_investment_monitor_indicator_status (company_id, status, deleted),
    CONSTRAINT fk_investment_monitor_indicator_company FOREIGN KEY (company_id)
        REFERENCES investment_company(id),
    CONSTRAINT chk_investment_monitor_indicator_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投后监管指标表';

CREATE TABLE investment_monitor_data (
    id BIGINT NOT NULL COMMENT '投后经营数据ID',
    company_id BIGINT NOT NULL COMMENT '被投资企业ID',
    indicator_id BIGINT NOT NULL COMMENT '投后指标ID',
    monitor_period VARCHAR(20) NOT NULL COMMENT '数据期间，规范原period字段',
    actual_value DECIMAL(18,4) NOT NULL COMMENT '实际值',
    data_source VARCHAR(100) NULL COMMENT '数据来源',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_monitor_data (indicator_id, monitor_period, delete_token),
    KEY idx_investment_monitor_data_company (company_id, monitor_period, deleted),
    CONSTRAINT fk_investment_monitor_data_indicator FOREIGN KEY (indicator_id, company_id)
        REFERENCES investment_monitor_indicator(id, company_id),
    CONSTRAINT chk_investment_monitor_data_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投后经营数据采集表';

CREATE TABLE investment_risk (
    id BIGINT NOT NULL COMMENT '投资风险ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    risk_type VARCHAR(50) NOT NULL COMMENT '政策/市场/经营/财务/退出等风险类型',
    risk_name VARCHAR(200) NOT NULL COMMENT '风险名称',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
    risk_description TEXT NOT NULL COMMENT '风险描述',
    response_measure TEXT NULL COMMENT '应对措施',
    responsible_person BIGINT NULL COMMENT '责任人员工ID',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
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
    KEY idx_investment_risk_person (responsible_person, status, deleted),
    CONSTRAINT fk_investment_risk_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT fk_investment_risk_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_investment_risk_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资风险管理表';

CREATE TABLE investment_income (
    id BIGINT NOT NULL COMMENT '投资收益ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    income_type VARCHAR(50) NOT NULL COMMENT 'DIVIDEND/EQUITY_APPRECIATION/TRANSFER/ASSET',
    income_date DATE NOT NULL COMMENT '收益日期',
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收益金额',
    income_source VARCHAR(200) NULL COMMENT '收益来源，规范原source字段',
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
    CONSTRAINT chk_investment_income_amount CHECK (income_amount >= 0),
    CONSTRAINT chk_investment_income_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资收益记录表';

CREATE TABLE investment_exit (
    id BIGINT NOT NULL COMMENT '投资退出ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    exit_type VARCHAR(50) NOT NULL COMMENT 'TRANSFER/REPURCHASE/LIQUIDATION/IPO',
    exit_date DATE NULL COMMENT '退出日期',
    exit_reason TEXT NULL COMMENT '退出原因',
    exit_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '退出金额',
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '退出收益',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
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
    KEY idx_investment_exit_approval (approval_status, status, deleted),
    CONSTRAINT fk_investment_exit_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_exit_amount CHECK (exit_amount >= 0 AND income_amount >= 0),
    CONSTRAINT chk_investment_exit_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资退出管理表';

CREATE TABLE investment_evaluation (
    id BIGINT NOT NULL COMMENT '投后评价ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    evaluation_date DATE NOT NULL COMMENT '评价日期',
    economic_score DECIMAL(5,2) NULL COMMENT '经济效益评分',
    management_score DECIMAL(5,2) NULL COMMENT '管理评分',
    risk_score DECIMAL(5,2) NULL COMMENT '风险控制评分',
    overall_score DECIMAL(5,2) NULL COMMENT '综合评分',
    summary TEXT NULL COMMENT '评价总结',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_investment_evaluation_item (investment_id, evaluation_date, delete_token),
    KEY idx_investment_evaluation_date (evaluation_date, deleted),
    CONSTRAINT fk_investment_evaluation_item FOREIGN KEY (investment_id)
        REFERENCES investment_project(id),
    CONSTRAINT chk_investment_evaluation_score CHECK (
        (economic_score IS NULL OR economic_score BETWEEN 0 AND 100)
        AND (management_score IS NULL OR management_score BETWEEN 0 AND 100)
        AND (risk_score IS NULL OR risk_score BETWEEN 0 AND 100)
        AND (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
    ),
    CONSTRAINT chk_investment_evaluation_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资后评价表';

SET FOREIGN_KEY_CHECKS = 1;
