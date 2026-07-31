-- ============================================
-- 组织人事数据库完整 SQL
-- 组织 -> 岗位 -> 人员 -> 干部 -> 绩效 -> 薪酬 -> 人才
-- 主键由应用雪花算法生成。
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE hr_position (
    id BIGINT NOT NULL COMMENT '岗位ID',
    position_code VARCHAR(50) NOT NULL COMMENT '岗位编码',
    position_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    org_id BIGINT NOT NULL COMMENT '所属部门ID',
    position_level VARCHAR(50) NULL COMMENT '岗位等级',
    job_description TEXT NULL COMMENT '岗位职责',
    qualification TEXT NULL COMMENT '任职资格',
    status SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_position_code (position_code, delete_token),
    KEY idx_hr_position_org (org_id, status, deleted),
    KEY idx_hr_position_level (position_level, status, deleted),
    CONSTRAINT fk_hr_position_org FOREIGN KEY (org_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位管理表';

CREATE TABLE hr_employee (
    id BIGINT NOT NULL COMMENT '员工ID',
    employee_no VARCHAR(50) NOT NULL COMMENT '员工编号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender VARCHAR(10) NULL COMMENT '性别',
    birthday DATE NULL COMMENT '出生日期',
    id_card VARCHAR(255) NULL COMMENT '身份证号，应用层加密存储',
    phone VARCHAR(255) NULL COMMENT '手机号，应用层加密存储',
    email VARCHAR(100) NULL,
    education VARCHAR(50) NULL COMMENT '学历',
    major VARCHAR(100) NULL COMMENT '专业',
    school VARCHAR(100) NULL COMMENT '毕业院校',
    political_status VARCHAR(50) NULL COMMENT '政治面貌',
    party_date DATE NULL COMMENT '入党时间',
    org_id BIGINT NOT NULL COMMENT '所属组织ID',
    position_id BIGINT NULL COMMENT '当前岗位ID',
    entry_date DATE NULL COMMENT '入职时间',
    employee_type VARCHAR(30) NOT NULL COMMENT 'FORMAL/DISPATCH/EXTERNAL/PROJECT',
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
    UNIQUE KEY uk_hr_employee_no (employee_no, delete_token),
    KEY idx_hr_employee_org (org_id, status, deleted),
    KEY idx_hr_employee_position (position_id, status, deleted),
    KEY idx_hr_employee_type (employee_type, status, deleted),
    CONSTRAINT fk_hr_employee_org FOREIGN KEY (org_id) REFERENCES sys_org(id),
    CONSTRAINT fk_hr_employee_position FOREIGN KEY (position_id) REFERENCES hr_position(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工基础档案表';

CREATE TABLE hr_employee_position (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    is_current SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_hr_employee_position_employee (employee_id, is_current, deleted, start_date),
    KEY idx_hr_employee_position_position (position_id, is_current, deleted),
    CONSTRAINT fk_hr_ep_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_hr_ep_position FOREIGN KEY (position_id) REFERENCES hr_position(id),
    CONSTRAINT chk_hr_ep_dates CHECK (end_date IS NULL OR start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工岗位履历表';

CREATE TABLE hr_three_definition (
    id BIGINT NOT NULL,
    definition_year INT NOT NULL COMMENT '年度，原设计字段year',
    org_id BIGINT NOT NULL COMMENT '部门ID',
    position_id BIGINT NOT NULL COMMENT '岗位ID',
    approved_number INT NOT NULL DEFAULT 0 COMMENT '核定人数',
    actual_number INT NOT NULL DEFAULT 0 COMMENT '实际人数',
    difference_number INT NOT NULL DEFAULT 0 COMMENT '差额=核定人数-实际人数',
    status VARCHAR(20) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_three_definition (
        definition_year, org_id, position_id, delete_token
    ),
    KEY idx_hr_three_definition_status (definition_year, status, deleted),
    CONSTRAINT fk_hr_td_org FOREIGN KEY (org_id) REFERENCES sys_org(id),
    CONSTRAINT fk_hr_td_position FOREIGN KEY (position_id) REFERENCES hr_position(id),
    CONSTRAINT chk_hr_td_number CHECK (
        approved_number >= 0 AND actual_number >= 0
        AND difference_number = approved_number - actual_number
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三定管理表';

CREATE TABLE hr_cadre (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    cadre_level VARCHAR(50) NOT NULL COMMENT '干部级别',
    current_position VARCHAR(100) NULL,
    appointment_date DATE NULL,
    term_start DATE NULL,
    term_end DATE NULL,
    political_evaluation TEXT NULL,
    performance_summary TEXT NULL,
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
    UNIQUE KEY uk_hr_cadre_employee (employee_id, delete_token),
    KEY idx_hr_cadre_level (cadre_level, status, deleted),
    KEY idx_hr_cadre_term_end (term_end, status, deleted),
    CONSTRAINT fk_hr_cadre_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_hr_cadre_term CHECK (
        term_start IS NULL OR term_end IS NULL OR term_start <= term_end
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='干部档案表';

CREATE TABLE hr_cadre_appointment (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    appointment_type VARCHAR(50) NOT NULL COMMENT '选拔/任职/免职/调整',
    before_position VARCHAR(100) NULL,
    after_position VARCHAR(100) NULL,
    approval_date DATE NOT NULL,
    approval_document VARCHAR(500) NULL COMMENT '审批文件ID或受控地址',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_hr_cadre_appointment_employee (employee_id, approval_date, deleted),
    KEY idx_hr_cadre_appointment_type (appointment_type, approval_date, deleted),
    CONSTRAINT fk_hr_ca_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='干部任免记录表';

CREATE TABLE hr_contractual_management (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    contract_position VARCHAR(100) NOT NULL COMMENT '任期岗位，原设计字段position',
    term_start DATE NOT NULL,
    term_end DATE NOT NULL,
    target_content TEXT NOT NULL COMMENT '任期目标',
    annual_target TEXT NULL COMMENT '年度目标',
    assessment_result VARCHAR(50) NULL,
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
    KEY idx_hr_contractual_employee (employee_id, status, deleted),
    KEY idx_hr_contractual_term (term_end, status, deleted),
    CONSTRAINT fk_hr_contractual_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_hr_contractual_term CHECK (term_start <= term_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任期制契约化管理表';

CREATE TABLE hr_training_course (
    id BIGINT NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    course_type VARCHAR(50) NOT NULL,
    teacher VARCHAR(100) NULL,
    hours INT NOT NULL DEFAULT 0,
    content TEXT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_hr_training_course_type (course_type, status, deleted),
    CONSTRAINT chk_hr_training_course_hours CHECK (hours >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训课程表';

CREATE TABLE hr_training_record (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    training_date DATE NOT NULL,
    score DECIMAL(5,2) NULL,
    certificate VARCHAR(200) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_training_record (
        employee_id, course_id, training_date, delete_token
    ),
    KEY idx_hr_training_record_course (course_id, training_date, deleted),
    CONSTRAINT fk_hr_training_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_hr_training_course FOREIGN KEY (course_id) REFERENCES hr_training_course(id),
    CONSTRAINT chk_hr_training_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工培训记录表';

CREATE TABLE hr_performance_indicator (
    id BIGINT NOT NULL,
    indicator_code VARCHAR(50) NOT NULL,
    indicator_name VARCHAR(200) NOT NULL,
    indicator_type VARCHAR(50) NOT NULL COMMENT 'COMPANY/DEPARTMENT/PERSONAL',
    weight DECIMAL(5,2) NOT NULL DEFAULT 0,
    target_value VARCHAR(100) NULL,
    score_rule TEXT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_indicator_code (indicator_code, delete_token),
    KEY idx_hr_indicator_type (indicator_type, status, deleted),
    CONSTRAINT chk_hr_indicator_weight CHECK (weight >= 0 AND weight <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效指标库';

CREATE TABLE hr_performance_record (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    indicator_id BIGINT NOT NULL,
    assessment_period VARCHAR(20) NOT NULL,
    target_value VARCHAR(100) NULL,
    actual_value VARCHAR(100) NULL,
    score DECIMAL(5,2) NULL,
    assessor_id BIGINT NULL COMMENT '考核人员工ID',
    assessment_date DATE NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_performance_record (
        employee_id, indicator_id, assessment_period, delete_token
    ),
    KEY idx_hr_performance_employee (employee_id, assessment_period, deleted),
    KEY idx_hr_performance_assessor (assessor_id, assessment_period, deleted),
    CONSTRAINT fk_hr_performance_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_hr_performance_indicator FOREIGN KEY (indicator_id)
        REFERENCES hr_performance_indicator(id),
    CONSTRAINT fk_hr_performance_assessor FOREIGN KEY (assessor_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_hr_performance_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工绩效考核记录表';

CREATE TABLE hr_salary_record (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    salary_month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    basic_salary DECIMAL(12,2) NOT NULL DEFAULT 0,
    performance_salary DECIMAL(12,2) NOT NULL DEFAULT 0,
    allowance DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_salary DECIMAL(12,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_salary_record (employee_id, salary_month, delete_token),
    KEY idx_hr_salary_month (salary_month, deleted),
    CONSTRAINT fk_hr_salary_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_hr_salary_amount CHECK (
        basic_salary >= 0 AND performance_salary >= 0 AND allowance >= 0
        AND total_salary = basic_salary + performance_salary + allowance
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工薪酬记录表';

CREATE TABLE hr_salary_budget (
    id BIGINT NOT NULL,
    budget_year INT NOT NULL COMMENT '年度，原设计字段year',
    org_id BIGINT NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remaining_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_salary_budget (budget_year, org_id, delete_token),
    KEY idx_hr_salary_budget_org (org_id, budget_year, deleted),
    CONSTRAINT fk_hr_salary_budget_org FOREIGN KEY (org_id) REFERENCES sys_org(id),
    CONSTRAINT chk_hr_salary_budget_amount CHECK (
        budget_amount >= 0 AND used_amount >= 0
        AND remaining_amount = budget_amount - used_amount
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工资总额预算表';

CREATE TABLE hr_talent_tag (
    id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    tag_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_talent_tag (tag_type, tag_name, delete_token),
    KEY idx_hr_talent_tag_type (tag_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人才标签库';

CREATE TABLE hr_employee_tag (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_employee_tag (employee_id, tag_id, delete_token),
    KEY idx_hr_employee_tag_tag (tag_id, deleted),
    CONSTRAINT fk_hr_employee_tag_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_hr_employee_tag_tag FOREIGN KEY (tag_id) REFERENCES hr_talent_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工人才标签关系表';

SET FOREIGN_KEY_CHECKS = 1;
