-- ============================================
-- 项目全生命周期管理数据库完整 SQL
-- 机会 -> 立项 -> 执行 -> 监控 -> 验收 -> 评价 -> 归档
-- 主键由应用雪花算法生成。
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE project_stage_template (
    id BIGINT NOT NULL COMMENT '项目阶段模板ID',
    project_type VARCHAR(50) NOT NULL DEFAULT 'ALL' COMMENT '项目类型；ALL表示通用模板',
    stage_code VARCHAR(50) NOT NULL COMMENT '阶段编码',
    stage_name VARCHAR(100) NOT NULL COMMENT '阶段名称',
    stage_order INT NOT NULL COMMENT '阶段顺序',
    requires_approval SMALLINT NOT NULL DEFAULT 0 COMMENT '是否需要审批：1是，0否',
    status SMALLINT NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_stage_template_code (project_type, stage_code, delete_token),
    UNIQUE KEY uk_project_stage_template_order (project_type, stage_order, delete_token),
    KEY idx_project_stage_template_status (project_type, status, deleted, stage_order),
    CONSTRAINT chk_project_stage_template_order CHECK (stage_order > 0),
    CONSTRAINT chk_project_stage_template_approval CHECK (requires_approval IN (0, 1)),
    CONSTRAINT chk_project_stage_template_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_project_stage_template_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段模板表';

CREATE TABLE project_info (
    id BIGINT NOT NULL COMMENT '项目ID',
    project_no VARCHAR(50) NOT NULL COMMENT '项目编号',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_type VARCHAR(50) NOT NULL COMMENT '01投资/02中标/03工程/04数字化/05研发/06运营',
    project_mode VARCHAR(50) NULL COMMENT '项目模式',
    source_type VARCHAR(50) NULL COMMENT '项目来源',
    customer_id BIGINT NULL COMMENT '客户单位逻辑引用',
    department_id BIGINT NOT NULL COMMENT '责任部门ID，规范原responsible_org_id',
    leader_id BIGINT NOT NULL COMMENT '项目负责人员工ID',
    start_date DATE NULL,
    end_date DATE NULL,
    actual_start_date DATE NULL,
    actual_end_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    budget_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    current_stage_code VARCHAR(32) NOT NULL DEFAULT 'RESERVE',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
    description TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_info_no (project_no, delete_token),
    KEY idx_project_info_status (status, deleted, create_time),
    KEY idx_project_info_type (project_type, status, deleted),
    KEY idx_project_info_department (department_id, status, deleted, create_time),
    KEY idx_project_info_leader (leader_id, status, deleted),
    KEY idx_project_info_stage (current_stage_code, deleted),
    CONSTRAINT fk_project_info_department FOREIGN KEY (department_id) REFERENCES sys_org(id),
    CONSTRAINT fk_project_info_leader FOREIGN KEY (leader_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_info_plan_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    ),
    CONSTRAINT chk_project_info_actual_dates CHECK (
        actual_start_date IS NULL OR actual_end_date IS NULL
        OR actual_start_date <= actual_end_date
    ),
    CONSTRAINT chk_project_info_amount CHECK (
        budget_amount >= 0 AND contract_amount >= 0 AND expected_income >= 0
        AND actual_income >= 0
    ),
    CONSTRAINT chk_project_info_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目主表';

CREATE TABLE project_stage (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_code VARCHAR(50) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    stage_order INT NOT NULL,
    start_time DATE NULL COMMENT '计划开始日期，规范原plan_start',
    end_time DATE NULL COMMENT '计划结束日期，规范原plan_end',
    actual_start_time DATE NULL,
    actual_end_time DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    responsible_person BIGINT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    completion_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_stage_code (project_id, stage_code, delete_token),
    UNIQUE KEY uk_project_stage_id_project (id, project_id),
    KEY idx_project_stage_order (project_id, deleted, stage_order),
    KEY idx_project_stage_status (status, end_time, deleted),
    CONSTRAINT fk_project_stage_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_stage_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_stage_plan_dates CHECK (
        start_time IS NULL OR end_time IS NULL OR start_time <= end_time
    ),
    CONSTRAINT chk_project_stage_actual_dates CHECK (
        actual_start_time IS NULL OR actual_end_time IS NULL
        OR actual_start_time <= actual_end_time
    ),
    CONSTRAINT chk_project_stage_progress CHECK (
        completion_percent >= 0 AND completion_percent <= 100
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段管理表';

CREATE TABLE project_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    parent_task_id BIGINT NULL COMMENT '父任务ID，规范原parent_id',
    task_no VARCHAR(64) NOT NULL COMMENT '项目内任务编号',
    task_name VARCHAR(200) NOT NULL,
    task_content TEXT NULL,
    responsible_person BIGINT NULL,
    plan_date DATE NULL COMMENT '当前API使用的计划完成日期',
    plan_start DATE NULL,
    plan_end DATE NULL,
    actual_date DATE NULL COMMENT '当前API使用的实际完成日期',
    actual_start DATE NULL,
    actual_end DATE NULL,
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    sort_no INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_task_no (project_id, task_no, delete_token),
    UNIQUE KEY uk_project_task_id_project (id, project_id),
    KEY idx_project_task_project (project_id, status, deleted, sort_no),
    KEY idx_project_task_stage (stage_id, status, deleted, sort_no),
    KEY idx_project_task_person (responsible_person, status, deleted),
    KEY idx_project_task_parent (parent_task_id, deleted),
    CONSTRAINT fk_project_task_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_task_stage FOREIGN KEY (stage_id, project_id)
        REFERENCES project_stage(id, project_id),
    CONSTRAINT fk_project_task_parent FOREIGN KEY (parent_task_id, project_id)
        REFERENCES project_task(id, project_id),
    CONSTRAINT fk_project_task_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_task_plan_dates CHECK (
        plan_start IS NULL OR plan_end IS NULL OR plan_start <= plan_end
    ),
    CONSTRAINT chk_project_task_actual_dates CHECK (
        actual_start IS NULL OR actual_end IS NULL OR actual_start <= actual_end
    ),
    CONSTRAINT chk_project_task_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目WBS任务表';

CREATE TABLE project_member (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL COMMENT '项目角色，规范原role_type',
    responsibilities VARCHAR(1000) NULL,
    joined_date DATE NOT NULL COMMENT '加入日期，规范原join_date',
    left_date DATE NULL COMMENT '离开日期，规范原leave_date',
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
    UNIQUE KEY uk_project_member_role (project_id, employee_id, role, delete_token),
    KEY idx_project_member_project (project_id, status, deleted),
    KEY idx_project_member_employee (employee_id, status, deleted),
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_member_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_member_dates CHECK (
        left_date IS NULL OR joined_date <= left_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目团队成员表';

CREATE TABLE project_milestone (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    milestone_name VARCHAR(200) NOT NULL,
    plan_date DATE NOT NULL,
    actual_date DATE NULL,
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
    KEY idx_project_milestone_project (project_id, status, plan_date, deleted),
    CONSTRAINT fk_project_milestone_project FOREIGN KEY (project_id) REFERENCES project_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目里程碑表';

CREATE TABLE project_investment_info (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    investment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    capital_source VARCHAR(100) NULL,
    investment_ratio DECIMAL(7,4) NULL,
    partner_name VARCHAR(200) NULL,
    spv_company VARCHAR(200) NULL,
    expected_roi DECIMAL(9,4) NULL,
    irr DECIMAL(9,4) NULL,
    payback_period DECIMAL(9,2) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_investment_info (project_id, delete_token),
    CONSTRAINT fk_project_investment_info_project FOREIGN KEY (project_id)
        REFERENCES project_info(id),
    CONSTRAINT chk_project_investment_amount CHECK (investment_amount >= 0),
    CONSTRAINT chk_project_investment_ratio CHECK (
        investment_ratio IS NULL OR investment_ratio BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_project_investment_payback CHECK (
        payback_period IS NULL OR payback_period >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资项目扩展信息表';

CREATE TABLE project_business_info (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    tender_no VARCHAR(100) NULL,
    bid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    win_date DATE NULL,
    delivery_period INT NULL COMMENT '交付周期，单位天',
    payment_method VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_business_info (project_id, delete_token),
    KEY idx_project_business_tender (tender_no, deleted),
    CONSTRAINT fk_project_business_info_project FOREIGN KEY (project_id)
        REFERENCES project_info(id),
    CONSTRAINT chk_project_business_amount CHECK (bid_amount >= 0),
    CONSTRAINT chk_project_business_delivery CHECK (
        delivery_period IS NULL OR delivery_period >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中标经营项目扩展表';

CREATE TABLE project_opportunity (
    id BIGINT NOT NULL,
    opportunity_no VARCHAR(50) NOT NULL,
    opportunity_name VARCHAR(200) NOT NULL,
    source VARCHAR(100) NULL,
    customer VARCHAR(200) NULL,
    estimated_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    responsible_person BIGINT NOT NULL,
    converted_project_id BIGINT NULL COMMENT '转化后的项目ID',
    status VARCHAR(20) NOT NULL DEFAULT 'DISCOVERED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_opportunity_no (opportunity_no, delete_token),
    KEY idx_project_opportunity_status (status, responsible_person, deleted),
    KEY idx_project_opportunity_project (converted_project_id, deleted),
    CONSTRAINT fk_project_opportunity_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id),
    CONSTRAINT fk_project_opportunity_project FOREIGN KEY (converted_project_id)
        REFERENCES project_info(id),
    CONSTRAINT chk_project_opportunity_amount CHECK (estimated_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目机会库';

CREATE TABLE project_bid (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    bid_no VARCHAR(100) NOT NULL,
    tender_company VARCHAR(200) NOT NULL,
    bid_date DATE NULL,
    bid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    result VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_bid_no (bid_no, delete_token),
    KEY idx_project_bid_project (project_id, bid_date, deleted),
    KEY idx_project_bid_result (result, bid_date, deleted),
    CONSTRAINT fk_project_bid_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_bid_amount CHECK (bid_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目投标记录表';

CREATE TABLE project_change (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    change_content TEXT NOT NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_change_project (project_id, approval_status, deleted, create_time),
    KEY idx_project_change_type (change_type, approval_status, deleted),
    CONSTRAINT fk_project_change_project FOREIGN KEY (project_id) REFERENCES project_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目变更记录表';

CREATE TABLE project_risk (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    risk_name VARCHAR(200) NOT NULL,
    risk_type VARCHAR(50) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    measure TEXT NULL,
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
    KEY idx_project_risk_project (project_id, status, deleted),
    KEY idx_project_risk_level (risk_level, risk_type, status, deleted),
    CONSTRAINT fk_project_risk_project FOREIGN KEY (project_id) REFERENCES project_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目风险表';

CREATE TABLE project_cost (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_type VARCHAR(50) NOT NULL COMMENT 'LABOR/PURCHASE/OUTSOURCE/EQUIPMENT/OPERATION/OTHER',
    cost_name VARCHAR(200) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_type VARCHAR(50) NULL,
    cost_date DATE NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_cost_project (project_id, cost_date, deleted),
    KEY idx_project_cost_type (cost_type, cost_date, deleted),
    CONSTRAINT fk_project_cost_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_cost_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成本明细表';

CREATE TABLE project_income (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    income_type VARCHAR(50) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    income_date DATE NOT NULL,
    source VARCHAR(200) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_income_project (project_id, income_date, deleted),
    KEY idx_project_income_type (income_type, income_date, deleted),
    CONSTRAINT fk_project_income_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_income_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目收入记录表';

CREATE TABLE project_profit (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    total_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit_rate DECIMAL(9,4) NULL,
    calculate_date DATE NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_profit_date (project_id, calculate_date, delete_token),
    KEY idx_project_profit_rate (profit_rate, calculate_date, deleted),
    CONSTRAINT fk_project_profit_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_profit_amount CHECK (
        total_income >= 0 AND total_cost >= 0 AND profit = total_income - total_cost
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目利润分析表';

CREATE TABLE project_acceptance (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    acceptance_date DATE NOT NULL,
    acceptance_type VARCHAR(50) NOT NULL,
    result VARCHAR(50) NOT NULL,
    customer_confirm VARCHAR(200) NULL,
    attachment VARCHAR(500) NULL COMMENT '附件ID或受控地址',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_acceptance_project (project_id, acceptance_date, deleted),
    KEY idx_project_acceptance_result (result, acceptance_date, deleted),
    CONSTRAINT fk_project_acceptance_project FOREIGN KEY (project_id) REFERENCES project_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目验收记录表';

CREATE TABLE project_evaluation (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    evaluation_date DATE NOT NULL,
    economic_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    management_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    customer_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    overall_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    summary TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_evaluation_project (project_id, evaluation_date, deleted),
    KEY idx_project_evaluation_score (overall_score, evaluation_date, deleted),
    CONSTRAINT fk_project_evaluation_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_evaluation_score CHECK (
        economic_score BETWEEN 0 AND 100
        AND management_score BETWEEN 0 AND 100
        AND customer_score BETWEEN 0 AND 100
        AND overall_score BETWEEN 0 AND 100
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目后评价表';

CREATE TABLE project_archive (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    archive_type VARCHAR(50) NOT NULL,
    file_id BIGINT NULL COMMENT '统一文件中心ID',
    file_name VARCHAR(200) NOT NULL,
    file_url VARCHAR(500) NULL COMMENT '历史文件地址或受控访问地址',
    archive_date DATE NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_archive_project (project_id, archive_type, archive_date, deleted),
    KEY idx_project_archive_file (file_id, deleted),
    CONSTRAINT fk_project_archive_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_archive_file FOREIGN KEY (file_id) REFERENCES sys_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目档案表';

SET FOREIGN_KEY_CHECKS = 1;
