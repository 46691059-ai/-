-- 国企数字化治理与经营赋能平台 V1.0
-- MySQL 8.x；单一物理库 enterprise_platform，使用模块前缀实现逻辑分域。
-- 主键由应用雪花算法生成。状态、类型字段使用 VARCHAR，便于适配达梦和人大金仓。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE sys_org (
    id BIGINT NOT NULL,
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    org_type VARCHAR(32) NOT NULL COMMENT 'COMPANY/DEPARTMENT/PROJECT_TEAM/PARTY_ORG',
    parent_id BIGINT NULL,
    leader_id BIGINT NULL COMMENT '负责人员工ID，员工表创建后补充外键',
    tree_path VARCHAR(1000) NOT NULL DEFAULT '/',
    tree_level INT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0,
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
    UNIQUE KEY uk_sys_org_code (org_code, delete_token),
    KEY idx_sys_org_parent (parent_id, deleted, sort_no),
    KEY idx_sys_org_type_status (org_type, status, deleted),
    CONSTRAINT fk_sys_org_parent FOREIGN KEY (parent_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构';

CREATE TABLE sys_role (
    id BIGINT NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    data_scope_type VARCHAR(32) NOT NULL DEFAULT 'SELF',
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
    UNIQUE KEY uk_sys_role_code (role_code, delete_token),
    KEY idx_sys_role_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL COMMENT 'API/BUTTON/DATA',
    resource_path VARCHAR(500) NULL,
    http_method VARCHAR(16) NULL,
    module_code VARCHAR(64) NOT NULL,
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
    UNIQUE KEY uk_sys_permission_code (permission_code, delete_token),
    KEY idx_sys_permission_module (module_code, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限资源';

CREATE TABLE sys_menu (
    id BIGINT NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    menu_type VARCHAR(16) NOT NULL COMMENT 'DIRECTORY/MENU/BUTTON/EXTERNAL',
    parent_id BIGINT NULL,
    path VARCHAR(255) NULL,
    component VARCHAR(255) NULL,
    permission VARCHAR(128) NULL,
    icon VARCHAR(64) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    visible SMALLINT NOT NULL DEFAULT 1,
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
    KEY idx_sys_menu_parent (parent_id, deleted, sort_no),
    KEY idx_sys_menu_permission (permission),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单';

CREATE TABLE sys_user (
    id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL COMMENT '仅保存BCrypt/Argon2摘要',
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    org_id BIGINT NOT NULL,
    employee_id BIGINT NULL COMMENT '员工表创建后补充外键',
    status SMALLINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    login_fail_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    last_login_time DATETIME(3) NULL,
    last_login_ip VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username, delete_token),
    UNIQUE KEY uk_sys_user_employee (employee_id, delete_token),
    KEY idx_sys_user_org_status (org_id, status, deleted),
    KEY idx_sys_user_phone (phone),
    CONSTRAINT fk_sys_user_org FOREIGN KEY (org_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id, delete_token),
    KEY idx_sys_user_role_role (role_id, deleted),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系';

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id, delete_token),
    KEY idx_sys_role_permission_permission (permission_id, deleted),
    CONSTRAINT fk_sys_rp_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_rp_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系';

CREATE TABLE sys_role_menu (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id, delete_token),
    KEY idx_sys_role_menu_menu (menu_id, deleted),
    CONSTRAINT fk_sys_rm_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_rm_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关系';

CREATE TABLE sys_role_org (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_org (role_id, org_id, delete_token),
    KEY idx_sys_role_org_org (org_id, deleted),
    CONSTRAINT fk_sys_ro_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_ro_org FOREIGN KEY (org_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色组织数据范围';

CREATE TABLE sys_log (
    id BIGINT NOT NULL,
    user_id BIGINT NULL,
    operation VARCHAR(255) NOT NULL,
    request_url VARCHAR(500) NULL,
    request_method VARCHAR(16) NULL,
    ip VARCHAR(64) NULL,
    result VARCHAR(32) NOT NULL,
    error_message VARCHAR(1000) NULL,
    duration_ms BIGINT NULL,
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
    KEY idx_sys_log_user_time (user_id, create_time),
    KEY idx_sys_log_result_time (result, create_time),
    KEY idx_sys_log_trace (trace_id),
    CONSTRAINT fk_sys_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

CREATE TABLE hr_position (
    id BIGINT NOT NULL,
    position_code VARCHAR(64) NOT NULL,
    position_name VARCHAR(128) NOT NULL,
    department_id BIGINT NOT NULL,
    position_level VARCHAR(32) NULL,
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
    UNIQUE KEY uk_hr_position_code (position_code, delete_token),
    KEY idx_hr_position_department (department_id, status, deleted),
    CONSTRAINT fk_hr_position_department FOREIGN KEY (department_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位';

CREATE TABLE hr_employee (
    id BIGINT NOT NULL,
    employee_no VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(16) NULL,
    birthday DATE NULL,
    id_card VARCHAR(255) NULL COMMENT '应用层加密存储',
    education VARCHAR(64) NULL,
    major VARCHAR(128) NULL,
    phone VARCHAR(255) NULL COMMENT '应用层加密存储',
    org_id BIGINT NOT NULL,
    position_id BIGINT NULL,
    political_status VARCHAR(32) NULL,
    entry_date DATE NULL,
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
    UNIQUE KEY uk_hr_employee_no (employee_no, delete_token),
    KEY idx_hr_employee_org_status (org_id, status, deleted),
    KEY idx_hr_employee_position (position_id, status, deleted),
    CONSTRAINT fk_hr_employee_org FOREIGN KEY (org_id) REFERENCES sys_org(id),
    CONSTRAINT fk_hr_employee_position FOREIGN KEY (position_id) REFERENCES hr_position(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工信息';

ALTER TABLE sys_user
    ADD CONSTRAINT fk_sys_user_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id);
ALTER TABLE sys_org
    ADD CONSTRAINT fk_sys_org_leader FOREIGN KEY (leader_id) REFERENCES hr_employee(id);

CREATE TABLE hr_three_definition (
    id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    approved_number INT NOT NULL DEFAULT 0,
    current_number INT NOT NULL DEFAULT 0,
    definition_year INT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_three_definition (org_id, position_id, definition_year, delete_token),
    CONSTRAINT fk_hr_td_org FOREIGN KEY (org_id) REFERENCES sys_org(id),
    CONSTRAINT fk_hr_td_position FOREIGN KEY (position_id) REFERENCES hr_position(id),
    CONSTRAINT chk_hr_td_number CHECK (approved_number >= 0 AND current_number >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三定管理';

CREATE TABLE hr_cadre (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    cadre_level VARCHAR(32) NULL,
    current_position VARCHAR(128) NULL,
    appointment_date DATE NULL,
    term_start DATE NULL,
    term_end DATE NULL,
    assessment_result VARCHAR(500) NULL,
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
    CONSTRAINT fk_hr_cadre_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_hr_cadre_term CHECK (term_start IS NULL OR term_end IS NULL OR term_start <= term_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='干部档案';

CREATE TABLE hr_performance_indicator (
    id BIGINT NOT NULL,
    indicator_name VARCHAR(128) NOT NULL,
    indicator_type VARCHAR(32) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 0,
    target_value DECIMAL(18,4) NULL,
    score_rule VARCHAR(1000) NULL,
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
    KEY idx_hr_indicator_type (indicator_type, status, deleted),
    CONSTRAINT chk_hr_indicator_weight CHECK (weight >= 0 AND weight <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效指标';

CREATE TABLE party_org (
    id BIGINT NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    org_type VARCHAR(32) NOT NULL,
    secretary_id BIGINT NULL,
    parent_id BIGINT NULL,
    sys_org_id BIGINT NULL,
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
    KEY idx_party_org_parent (parent_id, deleted),
    KEY idx_party_org_sys_org (sys_org_id, deleted),
    CONSTRAINT fk_party_org_parent FOREIGN KEY (parent_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_org_secretary FOREIGN KEY (secretary_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_party_org_sys_org FOREIGN KEY (sys_org_id) REFERENCES sys_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党组织';

CREATE TABLE party_member (
    id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    join_date DATE NOT NULL,
    positive_date DATE NULL,
    party_position VARCHAR(128) NULL,
    party_org_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_party_member_employee (employee_id, delete_token),
    KEY idx_party_member_org (party_org_id, status, deleted),
    CONSTRAINT fk_party_member_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_party_member_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员档案';

CREATE TABLE party_activity (
    id BIGINT NOT NULL,
    activity_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    activity_date DATETIME(3) NOT NULL,
    location VARCHAR(255) NULL,
    content TEXT NULL,
    party_org_id BIGINT NOT NULL,
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
    KEY idx_party_activity_org_date (party_org_id, activity_date, deleted),
    CONSTRAINT fk_party_activity_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织生活';

CREATE TABLE party_activity_member (
    id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    sign_status VARCHAR(32) NOT NULL DEFAULT 'UNSIGNED',
    sign_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_activity_member (activity_id, member_id, delete_token),
    KEY idx_party_activity_member_member (member_id, deleted),
    CONSTRAINT fk_party_am_activity FOREIGN KEY (activity_id) REFERENCES party_activity(id),
    CONSTRAINT fk_party_am_member FOREIGN KEY (member_id) REFERENCES party_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员活动签到';

CREATE TABLE project_info (
    id BIGINT NOT NULL,
    project_no VARCHAR(64) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    project_type VARCHAR(32) NOT NULL COMMENT 'INVESTMENT/OPERATION/ENGINEERING/DIGITAL/RD/OTHER',
    project_mode VARCHAR(32) NULL,
    leader_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    start_date DATE NULL,
    end_date DATE NULL,
    actual_start_date DATE NULL,
    actual_end_date DATE NULL,
    budget_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    current_stage_code VARCHAR(32) NOT NULL DEFAULT 'RESERVE',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
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
    UNIQUE KEY uk_project_info_no (project_no, delete_token),
    KEY idx_project_info_department (department_id, status, deleted, create_time),
    KEY idx_project_info_leader (leader_id, status, deleted),
    KEY idx_project_info_stage (current_stage_code, deleted),
    CONSTRAINT fk_project_info_leader FOREIGN KEY (leader_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_project_info_department FOREIGN KEY (department_id) REFERENCES sys_org(id),
    CONSTRAINT chk_project_info_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_project_info_amount CHECK (budget_amount >= 0 AND expected_income >= 0),
    CONSTRAINT chk_project_info_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目主表';

CREATE TABLE project_stage (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_code VARCHAR(32) NOT NULL,
    stage_name VARCHAR(64) NOT NULL,
    stage_order INT NOT NULL,
    start_time DATE NULL,
    end_time DATE NULL,
    actual_start_time DATE NULL,
    actual_end_time DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
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
    CONSTRAINT fk_project_stage_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_stage_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_stage_progress CHECK (completion_percent >= 0 AND completion_percent <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段';

CREATE TABLE project_task (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    parent_task_id BIGINT NULL,
    task_no VARCHAR(64) NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    responsible_person BIGINT NULL,
    plan_date DATE NULL,
    actual_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
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
    KEY idx_project_task_stage (stage_id, status, deleted, sort_no),
    KEY idx_project_task_person (responsible_person, status, deleted),
    CONSTRAINT fk_project_task_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_project_task_stage FOREIGN KEY (stage_id, project_id) REFERENCES project_stage(id, project_id),
    CONSTRAINT fk_project_task_parent FOREIGN KEY (parent_task_id, project_id) REFERENCES project_task(id, project_id),
    CONSTRAINT fk_project_task_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_project_task_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目任务';

CREATE TABLE project_member (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    responsibilities VARCHAR(1000) NULL,
    joined_date DATE NOT NULL,
    left_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
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
    CONSTRAINT chk_project_member_dates CHECK (left_date IS NULL OR joined_date <= left_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员';

CREATE TABLE operation_contract (
    id BIGINT NOT NULL,
    contract_no VARCHAR(64) NOT NULL,
    contract_name VARCHAR(200) NOT NULL,
    customer_id BIGINT NULL COMMENT '客户主数据预留逻辑引用',
    project_id BIGINT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    sign_date DATE NULL,
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
    UNIQUE KEY uk_operation_contract_no (contract_no, delete_token),
    KEY idx_operation_contract_project (project_id, status, deleted),
    CONSTRAINT fk_operation_contract_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_operation_contract_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同';

CREATE TABLE contract_payment (
    id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    payment_name VARCHAR(128) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    plan_date DATE NOT NULL,
    actual_date DATE NULL,
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
    KEY idx_contract_payment_contract (contract_id, status, plan_date, deleted),
    CONSTRAINT fk_contract_payment_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT chk_contract_payment_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同付款节点';

CREATE TABLE operation_income (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    income_amount DECIMAL(18,2) NOT NULL,
    income_date DATE NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_operation_income_project_date (project_id, income_date, deleted),
    KEY idx_operation_income_contract (contract_id, deleted),
    CONSTRAINT fk_operation_income_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_operation_income_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT chk_operation_income_amount CHECK (income_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目收入';

CREATE TABLE operation_cost (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
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
    KEY idx_operation_cost_project_date (project_id, cost_date, deleted),
    KEY idx_operation_cost_type (cost_type, deleted),
    CONSTRAINT fk_operation_cost_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_operation_cost_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成本';

CREATE TABLE project_profit (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    income DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    profit_rate DECIMAL(9,4) NULL,
    statistic_date DATE NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_profit_date (project_id, statistic_date, delete_token),
    CONSTRAINT fk_project_profit_project FOREIGN KEY (project_id) REFERENCES project_info(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目利润分析快照';

SET FOREIGN_KEY_CHECKS = 1;

-- 不内置默认账号和密码，只初始化角色、权限、菜单。
INSERT INTO sys_role (
    id, role_name, role_code, description, data_scope_type, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
) VALUES (
    1, '超级管理员', 'SUPER_ADMIN', '系统内置角色', 'ALL', 1,
    CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
);

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
) VALUES
    (2001, '项目查询', 'project:lifecycle:list', 'API', '/projects/**', 'GET', 'project', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (2002, '项目新增', 'project:lifecycle:create', 'API', '/projects', 'POST', 'project', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (2003, '项目修改', 'project:lifecycle:update', 'API', '/projects/**', 'PUT', 'project', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (2004, '项目删除', 'project:lifecycle:delete', 'API', '/projects/**', 'DELETE', 'project', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0);

INSERT INTO sys_menu (
    id, menu_name, menu_type, parent_id, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
) VALUES
    (200, '项目全生命周期', 'DIRECTORY', NULL, '/projects', NULL, NULL, 'Management', 20, 1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (210, '项目库', 'MENU', 200, '/projects', 'project/ProjectListView', 'project:lifecycle:list', 'List', 10, 1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0);

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
) VALUES
    (12001, 1, 2001, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (12002, 1, 2002, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (12003, 1, 2003, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (12004, 1, 2004, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0);

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
) VALUES
    (12200, 1, 200, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0),
    (12210, 1, 210, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0);
