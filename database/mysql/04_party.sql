-- ============================================
-- 党建治理数据库完整 SQL
-- 党委治理、党员管理、组织生活、党建考核、党建经营融合
-- 主键由应用雪花算法生成。
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE party_org (
    id BIGINT NOT NULL COMMENT '党组织ID',
    org_code VARCHAR(50) NOT NULL COMMENT '党组织编码',
    org_name VARCHAR(100) NOT NULL COMMENT '党组织名称',
    org_type VARCHAR(30) NOT NULL COMMENT 'PARTY_COMMITTEE/GENERAL_BRANCH/BRANCH/GROUP',
    parent_id BIGINT NULL COMMENT '上级党组织ID，根组织为空',
    sys_org_id BIGINT NULL COMMENT '关联统一组织ID',
    secretary_id BIGINT NULL COMMENT '书记员工ID',
    member_count INT NOT NULL DEFAULT 0 COMMENT '党员数量',
    establish_date DATE NULL COMMENT '成立日期',
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
    UNIQUE KEY uk_party_org_code (org_code, delete_token),
    KEY idx_party_org_parent (parent_id, status, deleted),
    KEY idx_party_org_sys_org (sys_org_id, deleted),
    KEY idx_party_org_secretary (secretary_id, status, deleted),
    CONSTRAINT fk_party_org_parent FOREIGN KEY (parent_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_org_sys_org FOREIGN KEY (sys_org_id) REFERENCES sys_org(id),
    CONSTRAINT fk_party_org_secretary FOREIGN KEY (secretary_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_party_org_member_count CHECK (member_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党组织信息表';

CREATE TABLE party_member (
    id BIGINT NOT NULL COMMENT '党员ID',
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    party_org_id BIGINT NOT NULL COMMENT '所属党组织ID',
    party_status VARCHAR(30) NOT NULL COMMENT 'PREPARATORY/FORMAL',
    apply_date DATE NULL COMMENT '申请入党日期',
    activist_date DATE NULL COMMENT '积极分子日期',
    development_date DATE NULL COMMENT '发展对象日期',
    prepare_date DATE NULL COMMENT '预备党员日期',
    positive_date DATE NULL COMMENT '转正日期',
    party_position VARCHAR(100) NULL COMMENT '党内职务',
    join_party_date DATE NULL COMMENT '入党时间',
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
    KEY idx_party_member_org (party_org_id, party_status, status, deleted),
    CONSTRAINT fk_party_member_employee FOREIGN KEY (employee_id) REFERENCES hr_employee(id),
    CONSTRAINT fk_party_member_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员档案表';

CREATE TABLE party_member_development (
    id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    stage VARCHAR(50) NOT NULL COMMENT 'APPLICATION/ACTIVIST/CANDIDATE/PREPARATORY/FORMAL',
    record_date DATE NOT NULL,
    responsible_person BIGINT NULL COMMENT '培养联系人员工ID',
    content TEXT NULL,
    attachment VARCHAR(500) NULL COMMENT '附件ID或受控地址',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_development_stage (member_id, stage, record_date, delete_token),
    KEY idx_party_development_approval (approval_status, record_date, deleted),
    CONSTRAINT fk_party_development_member FOREIGN KEY (member_id) REFERENCES party_member(id),
    CONSTRAINT fk_party_development_person FOREIGN KEY (responsible_person)
        REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员发展流程记录表';

CREATE TABLE party_fee_record (
    id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    fee_month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    income_base DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '缴费基数',
    should_pay DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '应缴金额',
    actual_pay DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实缴金额',
    pay_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_fee_month (member_id, fee_month, delete_token),
    KEY idx_party_fee_status (fee_month, status, deleted),
    CONSTRAINT fk_party_fee_member FOREIGN KEY (member_id) REFERENCES party_member(id),
    CONSTRAINT chk_party_fee_amount CHECK (
        income_base >= 0 AND should_pay >= 0 AND actual_pay >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员党费缴纳记录表';

CREATE TABLE party_activity_plan (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    plan_year INT NOT NULL COMMENT '年度，原设计字段year',
    plan_name VARCHAR(200) NOT NULL,
    plan_content TEXT NULL,
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
    UNIQUE KEY uk_party_activity_plan (party_org_id, plan_year, plan_name, delete_token),
    KEY idx_party_activity_plan_status (plan_year, status, deleted),
    CONSTRAINT fk_party_activity_plan_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织生活年度计划表';

CREATE TABLE party_activity (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL COMMENT 'COMMITTEE/MEMBER_ASSEMBLY/GROUP/LECTURE/THEME_DAY',
    title VARCHAR(200) NOT NULL,
    activity_date DATETIME(3) NOT NULL,
    location VARCHAR(200) NULL,
    host_id BIGINT NULL COMMENT '主持人员工ID',
    content TEXT NULL,
    summary TEXT NULL,
    attachment VARCHAR(500) NULL COMMENT '附件ID或受控地址',
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
    KEY idx_party_activity_org_date (party_org_id, activity_date, deleted),
    KEY idx_party_activity_type_date (activity_type, activity_date, deleted),
    CONSTRAINT fk_party_activity_org FOREIGN KEY (party_org_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_activity_host FOREIGN KEY (host_id) REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织生活活动表';

CREATE TABLE party_activity_member (
    id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    sign_status VARCHAR(20) NOT NULL DEFAULT 'UNSIGNED',
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
    KEY idx_party_activity_member_member (member_id, sign_status, deleted),
    CONSTRAINT fk_party_am_activity FOREIGN KEY (activity_id) REFERENCES party_activity(id),
    CONSTRAINT fk_party_am_member FOREIGN KEY (member_id) REFERENCES party_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党建活动参与记录表';

CREATE TABLE party_theme_day (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    theme VARCHAR(200) NOT NULL,
    activity_date DATE NOT NULL,
    activity_content TEXT NULL,
    innovation_point TEXT NULL,
    achievement TEXT NULL,
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
    KEY idx_party_theme_day_org (party_org_id, activity_date, deleted),
    CONSTRAINT fk_party_theme_day_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题党日活动表';

CREATE TABLE party_member_review (
    id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    review_year INT NOT NULL,
    self_score DECIMAL(5,2) NULL,
    organization_score DECIMAL(5,2) NULL,
    democratic_score DECIMAL(5,2) NULL,
    final_result VARCHAR(50) NOT NULL COMMENT 'EXCELLENT/QUALIFIED/BASIC_QUALIFIED/UNQUALIFIED',
    review_content TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_member_review (member_id, review_year, delete_token),
    KEY idx_party_member_review_result (review_year, final_result, deleted),
    CONSTRAINT fk_party_review_member FOREIGN KEY (member_id) REFERENCES party_member(id),
    CONSTRAINT chk_party_review_scores CHECK (
        (self_score IS NULL OR self_score BETWEEN 0 AND 100)
        AND (organization_score IS NULL OR organization_score BETWEEN 0 AND 100)
        AND (democratic_score IS NULL OR democratic_score BETWEEN 0 AND 100)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='民主评议党员表';

CREATE TABLE party_assessment_indicator (
    id BIGINT NOT NULL,
    indicator_code VARCHAR(50) NOT NULL,
    indicator_name VARCHAR(200) NOT NULL,
    indicator_type VARCHAR(50) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 0,
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
    UNIQUE KEY uk_party_assessment_indicator (indicator_code, delete_token),
    KEY idx_party_assessment_indicator_type (indicator_type, status, deleted),
    CONSTRAINT chk_party_assessment_weight CHECK (weight >= 0 AND weight <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党建考核指标库';

CREATE TABLE party_assessment_record (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    indicator_id BIGINT NOT NULL,
    assessment_year INT NOT NULL,
    score DECIMAL(5,2) NOT NULL DEFAULT 0,
    assessment_person BIGINT NULL COMMENT '考核人员工ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_assessment_record (
        party_org_id, indicator_id, assessment_year, delete_token
    ),
    KEY idx_party_assessment_record_year (assessment_year, score, deleted),
    CONSTRAINT fk_party_assessment_record_org FOREIGN KEY (party_org_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_assessment_record_indicator FOREIGN KEY (indicator_id)
        REFERENCES party_assessment_indicator(id),
    CONSTRAINT fk_party_assessment_record_person FOREIGN KEY (assessment_person)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_party_assessment_score CHECK (score >= 0 AND score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党建责任制考核记录表';

CREATE TABLE party_project (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL COMMENT '关联经营项目，后续脚本补充外键',
    party_org_id BIGINT NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    leader_id BIGINT NOT NULL COMMENT '负责人员工ID',
    member_count INT NOT NULL DEFAULT 0,
    party_goal TEXT NULL,
    achievement TEXT NULL,
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
    UNIQUE KEY uk_party_project (project_id, party_org_id, delete_token),
    KEY idx_party_project_org (party_org_id, status, deleted),
    KEY idx_party_project_leader (leader_id, status, deleted),
    CONSTRAINT fk_party_project_org FOREIGN KEY (party_org_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_project_leader FOREIGN KEY (leader_id) REFERENCES hr_employee(id),
    CONSTRAINT chk_party_project_member_count CHECK (member_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党建融合项目表';

CREATE TABLE party_position_area (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    area_name VARCHAR(200) NOT NULL,
    responsible_member BIGINT NOT NULL COMMENT '责任党员ID',
    responsibility TEXT NOT NULL,
    achievement TEXT NULL,
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
    KEY idx_party_position_area_org (party_org_id, status, deleted),
    KEY idx_party_position_area_member (responsible_member, status, deleted),
    CONSTRAINT fk_party_position_area_org FOREIGN KEY (party_org_id) REFERENCES party_org(id),
    CONSTRAINT fk_party_position_area_member FOREIGN KEY (responsible_member)
        REFERENCES party_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员责任区先锋岗表';

CREATE TABLE party_major_decision (
    id BIGINT NOT NULL,
    decision_no VARCHAR(50) NOT NULL,
    decision_name VARCHAR(200) NOT NULL,
    decision_type VARCHAR(50) NOT NULL COMMENT 'INVESTMENT/PROJECT/FUND/CADRE/OTHER',
    apply_department BIGINT NOT NULL,
    investment_id BIGINT NULL COMMENT '关联投资事项，后续脚本补充外键',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    party_opinion TEXT NULL,
    board_result TEXT NULL,
    execution_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
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
    UNIQUE KEY uk_party_major_decision_no (decision_no, delete_token),
    KEY idx_party_major_decision_dept (apply_department, execution_status, deleted),
    KEY idx_party_major_decision_type (decision_type, execution_status, deleted),
    KEY idx_party_major_decision_investment (investment_id, deleted),
    CONSTRAINT fk_party_major_decision_dept FOREIGN KEY (apply_department) REFERENCES sys_org(id),
    CONSTRAINT chk_party_major_decision_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三重一大决策事项表';

CREATE TABLE party_meeting (
    id BIGINT NOT NULL,
    meeting_type VARCHAR(50) NOT NULL,
    meeting_date DATETIME(3) NOT NULL,
    host_id BIGINT NULL COMMENT '主持人员工ID',
    participants TEXT NULL COMMENT '参与人员快照',
    agenda TEXT NULL,
    decision TEXT NULL,
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
    KEY idx_party_meeting_type_date (meeting_type, meeting_date, deleted),
    KEY idx_party_meeting_host (host_id, meeting_date, deleted),
    CONSTRAINT fk_party_meeting_host FOREIGN KEY (host_id) REFERENCES hr_employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党委会议记录表';

CREATE TABLE party_honor (
    id BIGINT NOT NULL,
    party_org_id BIGINT NOT NULL,
    honor_name VARCHAR(200) NOT NULL,
    honor_level VARCHAR(50) NULL COMMENT '荣誉级别，原设计字段level',
    obtain_date DATE NOT NULL,
    description TEXT NULL,
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
    KEY idx_party_honor_org_date (party_org_id, obtain_date, deleted),
    KEY idx_party_honor_level (honor_level, obtain_date, deleted),
    CONSTRAINT fk_party_honor_org FOREIGN KEY (party_org_id) REFERENCES party_org(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党建荣誉成果表';

SET FOREIGN_KEY_CHECKS = 1;
