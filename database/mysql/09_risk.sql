-- ============================================
-- 国企数字化治理与经营赋能平台
-- 风险合规管理数据库完整 SQL
-- 风险识别 -> 评估 -> 预警 -> 整改 -> 验证 -> 关闭
-- 主键由应用雪花算法生成，兼顾 MySQL、达梦和人大金仓迁移
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE risk_category (
    id BIGINT NOT NULL COMMENT '风险分类ID',
    category_code VARCHAR(50) NOT NULL COMMENT '风险分类编码',
    category_name VARCHAR(100) NOT NULL COMMENT '风险分类名称',
    parent_id BIGINT NULL COMMENT '上级风险分类ID',
    description VARCHAR(500) NULL COMMENT '分类说明',
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
    UNIQUE KEY uk_risk_category_code (category_code, delete_token),
    KEY idx_risk_category_parent (parent_id, status, deleted),
    CONSTRAINT fk_risk_category_parent FOREIGN KEY (parent_id) REFERENCES risk_category(id),
    CONSTRAINT chk_risk_category_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_risk_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险分类表';

CREATE TABLE risk_info (
    id BIGINT NOT NULL COMMENT '风险ID',
    risk_no VARCHAR(50) NOT NULL COMMENT '风险编号',
    risk_name VARCHAR(200) NOT NULL COMMENT '风险名称',
    category_id BIGINT NOT NULL COMMENT '风险分类ID',
    business_type VARCHAR(50) NOT NULL COMMENT '关联业务类型',
    business_id BIGINT NULL COMMENT '关联业务对象ID，多态逻辑引用',
    risk_level VARCHAR(20) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    probability VARCHAR(20) NULL COMMENT '发生概率等级',
    impact_level VARCHAR(20) NULL COMMENT '影响程度等级',
    description TEXT NOT NULL COMMENT '风险描述',
    responsible_org BIGINT NOT NULL COMMENT '责任组织ID',
    responsible_person BIGINT NULL COMMENT '责任人员工ID',
    discovery_source VARCHAR(50) NULL COMMENT '发现来源',
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
    UNIQUE KEY uk_risk_info_no (risk_no, delete_token),
    KEY idx_risk_info_category (category_id, risk_level, status, deleted),
    KEY idx_risk_info_business (business_type, business_id, status, deleted),
    KEY idx_risk_info_org (responsible_org, status, deleted),
    KEY idx_risk_info_person (responsible_person, status, deleted),
    KEY idx_risk_info_level (risk_level, status, deleted),
    CONSTRAINT fk_risk_info_category FOREIGN KEY (category_id) REFERENCES risk_category(id),
    CONSTRAINT fk_risk_info_org FOREIGN KEY (responsible_org) REFERENCES sys_org(id),
    CONSTRAINT fk_risk_info_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_info_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险信息主表';

CREATE TABLE risk_evaluation (
    id BIGINT NOT NULL COMMENT '风险评估ID',
    risk_id BIGINT NOT NULL COMMENT '风险ID',
    evaluation_date DATE NOT NULL COMMENT '评估日期',
    probability_score INT NOT NULL COMMENT '概率评分，1至5分',
    impact_score INT NOT NULL COMMENT '影响评分，1至5分',
    risk_score INT NOT NULL COMMENT '风险值，概率评分乘影响评分',
    evaluation_result VARCHAR(50) NULL COMMENT '评估结果',
    evaluator BIGINT NOT NULL COMMENT '评价人员工ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_evaluation_date (risk_id, evaluation_date, delete_token),
    KEY idx_risk_evaluation_score (risk_score, evaluation_date, deleted),
    KEY idx_risk_evaluation_user (evaluator, evaluation_date, deleted),
    CONSTRAINT fk_risk_evaluation_risk FOREIGN KEY (risk_id) REFERENCES risk_info(id),
    CONSTRAINT fk_risk_evaluation_user FOREIGN KEY (evaluator) REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_evaluation_score CHECK (
        probability_score BETWEEN 1 AND 5
        AND impact_score BETWEEN 1 AND 5
        AND risk_score = probability_score * impact_score
    ),
    CONSTRAINT chk_risk_evaluation_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险评估记录表';

CREATE TABLE risk_warning_rule (
    id BIGINT NOT NULL COMMENT '预警规则ID',
    rule_code VARCHAR(50) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(200) NOT NULL COMMENT '规则名称',
    business_type VARCHAR(50) NOT NULL COMMENT '适用业务类型',
    condition_expression TEXT NOT NULL COMMENT '受控DSL或JSON表达式，禁止存储或执行原生SQL',
    warning_level VARCHAR(20) NOT NULL COMMENT '预警等级',
    warning_message VARCHAR(500) NOT NULL COMMENT '预警消息模板',
    enabled SMALLINT NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_warning_rule_code (rule_code, delete_token),
    KEY idx_risk_warning_rule_business (business_type, enabled, deleted),
    KEY idx_risk_warning_rule_level (warning_level, enabled, deleted),
    CONSTRAINT chk_risk_warning_rule_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_risk_warning_rule_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险预警规则表';

CREATE TABLE risk_warning (
    id BIGINT NOT NULL COMMENT '风险预警ID',
    risk_id BIGINT NOT NULL COMMENT '风险ID',
    rule_id BIGINT NOT NULL COMMENT '触发规则ID',
    warning_level VARCHAR(20) NOT NULL COMMENT '预警等级',
    warning_content TEXT NOT NULL COMMENT '预警内容',
    trigger_time DATETIME(3) NOT NULL COMMENT '触发时间',
    handler_id BIGINT NULL COMMENT '处理人员工ID',
    handle_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handle_result TEXT NULL COMMENT '处理结果',
    handled_time DATETIME(3) NULL COMMENT '处理完成时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_warning_risk (risk_id, handle_status, deleted),
    KEY idx_risk_warning_rule (rule_id, trigger_time, deleted),
    KEY idx_risk_warning_status (handle_status, warning_level, trigger_time, deleted),
    KEY idx_risk_warning_handler (handler_id, handle_status, deleted),
    CONSTRAINT fk_risk_warning_risk FOREIGN KEY (risk_id) REFERENCES risk_info(id),
    CONSTRAINT fk_risk_warning_rule FOREIGN KEY (rule_id) REFERENCES risk_warning_rule(id),
    CONSTRAINT fk_risk_warning_handler FOREIGN KEY (handler_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_warning_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险预警记录表';

CREATE TABLE risk_rectification (
    id BIGINT NOT NULL COMMENT '风险整改ID',
    risk_id BIGINT NOT NULL COMMENT '风险ID',
    problem_description TEXT NOT NULL COMMENT '问题描述',
    rectification_measure TEXT NOT NULL COMMENT '整改措施',
    responsible_person BIGINT NOT NULL COMMENT '整改责任人员工ID',
    plan_finish_date DATE NOT NULL COMMENT '计划完成日期',
    actual_finish_date DATE NULL COMMENT '实际完成日期',
    verification_person BIGINT NULL COMMENT '验证人员工ID',
    verification_result VARCHAR(50) NULL COMMENT '验证结果',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
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
    KEY idx_risk_rectification_due (plan_finish_date, status, deleted),
    KEY idx_risk_rectification_person (responsible_person, status, deleted),
    CONSTRAINT fk_risk_rectification_risk FOREIGN KEY (risk_id) REFERENCES risk_info(id),
    CONSTRAINT fk_risk_rectification_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id),
    CONSTRAINT fk_risk_rectification_verifier FOREIGN KEY (verification_person)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_rectification_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险整改任务表';

CREATE TABLE risk_control_process (
    id BIGINT NOT NULL COMMENT '内控流程ID',
    process_code VARCHAR(50) NOT NULL COMMENT '流程编码',
    process_name VARCHAR(200) NOT NULL COMMENT '流程名称',
    business_domain VARCHAR(100) NOT NULL COMMENT '业务领域',
    control_target TEXT NOT NULL COMMENT '控制目标',
    control_measure TEXT NOT NULL COMMENT '控制措施',
    responsible_department BIGINT NOT NULL COMMENT '责任部门ID',
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
    UNIQUE KEY uk_risk_control_process_code (process_code, delete_token),
    KEY idx_risk_control_process_dept (responsible_department, status, deleted),
    KEY idx_risk_control_process_domain (business_domain, status, deleted),
    CONSTRAINT fk_risk_control_process_dept FOREIGN KEY (responsible_department)
        REFERENCES sys_org(id),
    CONSTRAINT chk_risk_control_process_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内控流程表';

CREATE TABLE risk_control_point (
    id BIGINT NOT NULL COMMENT '内控控制点ID',
    process_id BIGINT NOT NULL COMMENT '内控流程ID',
    control_name VARCHAR(200) NOT NULL COMMENT '控制点名称',
    control_type VARCHAR(50) NOT NULL COMMENT 'PRE/EVENT/POST',
    control_requirement TEXT NOT NULL COMMENT '控制要求',
    check_frequency VARCHAR(50) NULL COMMENT '检查频率',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_control_point_name (process_id, control_name, delete_token),
    KEY idx_risk_control_point_process (process_id, status, deleted, sort_no),
    CONSTRAINT fk_risk_control_point_process FOREIGN KEY (process_id)
        REFERENCES risk_control_process(id),
    CONSTRAINT chk_risk_control_point_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内控控制点表';

CREATE TABLE risk_audit_project (
    id BIGINT NOT NULL COMMENT '审计项目ID',
    audit_no VARCHAR(50) NOT NULL COMMENT '审计项目编号',
    audit_name VARCHAR(200) NOT NULL COMMENT '审计项目名称',
    audit_type VARCHAR(50) NOT NULL COMMENT 'SPECIAL/ECONOMIC_RESPONSIBILITY/INVESTMENT/FINANCE',
    audit_department BIGINT NOT NULL COMMENT '审计部门ID',
    start_date DATE NULL COMMENT '开始日期',
    end_date DATE NULL COMMENT '结束日期',
    audit_person BIGINT NOT NULL COMMENT '审计负责人员工ID',
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
    UNIQUE KEY uk_risk_audit_project_no (audit_no, delete_token),
    KEY idx_risk_audit_project_dept (audit_department, status, deleted),
    KEY idx_risk_audit_project_person (audit_person, status, deleted),
    KEY idx_risk_audit_project_dates (start_date, end_date, status, deleted),
    CONSTRAINT fk_risk_audit_project_dept FOREIGN KEY (audit_department) REFERENCES sys_org(id),
    CONSTRAINT fk_risk_audit_project_person FOREIGN KEY (audit_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_audit_project_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    ),
    CONSTRAINT chk_risk_audit_project_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部审计项目表';

CREATE TABLE risk_audit_problem (
    id BIGINT NOT NULL COMMENT '审计问题ID',
    audit_id BIGINT NOT NULL COMMENT '审计项目ID',
    problem_title VARCHAR(200) NOT NULL COMMENT '问题标题',
    problem_content TEXT NOT NULL COMMENT '问题内容',
    problem_level VARCHAR(20) NOT NULL COMMENT '问题等级',
    responsible_department BIGINT NOT NULL COMMENT '责任部门ID',
    responsible_person BIGINT NULL COMMENT '责任人员工ID',
    rectification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deadline DATE NOT NULL COMMENT '整改截止日期',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_audit_problem_audit (audit_id, rectification_status, deleted),
    KEY idx_risk_audit_problem_dept (responsible_department, rectification_status, deleted),
    KEY idx_risk_audit_problem_due (deadline, rectification_status, deleted),
    KEY idx_risk_audit_problem_level (problem_level, rectification_status, deleted),
    CONSTRAINT fk_risk_audit_problem_audit FOREIGN KEY (audit_id)
        REFERENCES risk_audit_project(id),
    CONSTRAINT fk_risk_audit_problem_dept FOREIGN KEY (responsible_department)
        REFERENCES sys_org(id),
    CONSTRAINT fk_risk_audit_problem_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_audit_problem_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计问题表';

CREATE TABLE risk_inspection_problem (
    id BIGINT NOT NULL COMMENT '巡察问题ID',
    inspection_batch VARCHAR(100) NOT NULL COMMENT '巡察批次',
    problem_title VARCHAR(200) NOT NULL COMMENT '问题标题',
    problem_content TEXT NOT NULL COMMENT '问题内容',
    responsible_org BIGINT NOT NULL COMMENT '责任组织ID',
    responsible_person BIGINT NULL COMMENT '责任人员工ID',
    rectification_measure TEXT NULL COMMENT '整改措施',
    deadline DATE NOT NULL COMMENT '整改截止日期',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_inspection_batch (inspection_batch, status, deleted),
    KEY idx_risk_inspection_org (responsible_org, status, deleted),
    KEY idx_risk_inspection_due (deadline, status, deleted),
    CONSTRAINT fk_risk_inspection_org FOREIGN KEY (responsible_org) REFERENCES sys_org(id),
    CONSTRAINT fk_risk_inspection_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_risk_inspection_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡察整改问题表';

CREATE TABLE risk_integrity (
    id BIGINT NOT NULL COMMENT '廉洁风险ID',
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    position_name VARCHAR(100) NOT NULL COMMENT '岗位名称，规范原position字段',
    risk_point VARCHAR(200) NOT NULL COMMENT '廉洁风险点',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
    prevention_measure TEXT NOT NULL COMMENT '防控措施',
    responsible_department BIGINT NOT NULL COMMENT '责任部门ID',
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
    KEY idx_risk_integrity_employee (employee_id, status, deleted),
    KEY idx_risk_integrity_dept (responsible_department, risk_level, status, deleted),
    CONSTRAINT fk_risk_integrity_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_risk_integrity_dept FOREIGN KEY (responsible_department) REFERENCES sys_org(id),
    CONSTRAINT chk_risk_integrity_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='廉洁风险管理表';

CREATE TABLE risk_contract (
    id BIGINT NOT NULL COMMENT '合同风险ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    risk_type VARCHAR(50) NOT NULL COMMENT '合同风险类型',
    risk_description TEXT NOT NULL COMMENT '风险描述',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
    handling_measure TEXT NULL COMMENT '处置措施',
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
    KEY idx_risk_contract_contract (contract_id, status, deleted),
    KEY idx_risk_contract_level (risk_level, status, deleted),
    CONSTRAINT fk_risk_contract_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT chk_risk_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同风险表';

-- 以下两表分别由项目域和投资域先行创建；兼容定义用于单独阅读和受控初始化。
CREATE TABLE IF NOT EXISTS project_risk (
    id BIGINT NOT NULL COMMENT '项目风险ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    risk_name VARCHAR(200) NOT NULL COMMENT '风险名称',
    risk_type VARCHAR(50) NOT NULL COMMENT '风险类型',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级',
    description TEXT NOT NULL COMMENT '风险描述',
    measure TEXT NULL COMMENT '应对措施',
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
    CONSTRAINT fk_project_risk_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_project_risk_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目风险表';

CREATE TABLE IF NOT EXISTS investment_risk (
    id BIGINT NOT NULL COMMENT '投资风险ID',
    investment_id BIGINT NOT NULL COMMENT '投资事项ID',
    risk_type VARCHAR(50) NOT NULL COMMENT '风险类型',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资风险表';

SET FOREIGN_KEY_CHECKS = 1;
