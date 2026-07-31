-- Project lifecycle module
-- Target: MySQL 8.x

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS pm_project (
    id BIGINT NOT NULL COMMENT '主键',
    project_code VARCHAR(64) NOT NULL COMMENT '项目唯一编码',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_type VARCHAR(32) NOT NULL COMMENT '项目类型：INVESTMENT/ENGINEERING/DIGITAL/OPERATION/OTHER',
    org_id BIGINT NOT NULL COMMENT '所属组织ID',
    manager_user_id BIGINT NOT NULL COMMENT '项目负责人用户ID',
    description VARCHAR(2000) NULL COMMENT '项目说明',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    actual_start_date DATE NULL COMMENT '实际开始日期',
    actual_end_date DATE NULL COMMENT '实际结束日期',
    investment_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '投资金额',
    expected_income DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '预计收益',
    actual_income DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '实际收益',
    current_stage_code VARCHAR(32) NOT NULL DEFAULT 'RESERVE' COMMENT '当前阶段编码',
    project_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '项目进度，0-100',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '删除标识：0正常，1删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_code (project_code),
    KEY idx_pm_project_org_status (org_id, project_status, deleted),
    KEY idx_pm_project_manager_status (manager_user_id, project_status, deleted),
    KEY idx_pm_project_stage (current_stage_code, deleted),
    KEY idx_pm_project_name (project_name),
    CONSTRAINT fk_pm_project_org FOREIGN KEY (org_id) REFERENCES sys_org (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pm_project_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_pm_project_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT chk_pm_project_dates CHECK (
        planned_start_date IS NULL OR planned_end_date IS NULL OR planned_start_date <= planned_end_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目主表';

CREATE TABLE IF NOT EXISTS pm_project_stage (
    id BIGINT NOT NULL COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    stage_code VARCHAR(32) NOT NULL COMMENT '阶段编码',
    stage_name VARCHAR(64) NOT NULL COMMENT '阶段名称',
    stage_order INT NOT NULL COMMENT '阶段顺序',
    stage_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '阶段状态',
    owner_user_id BIGINT NULL COMMENT '阶段负责人',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    actual_start_date DATE NULL COMMENT '实际开始日期',
    actual_end_date DATE NULL COMMENT '实际结束日期',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '审批状态',
    completion_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '完成百分比',
    milestone_desc VARCHAR(1000) NULL COMMENT '里程碑说明',
    risk_summary VARCHAR(1000) NULL COMMENT '阶段风险摘要',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '删除标识：0正常，1删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_stage_code (project_id, stage_code),
    KEY idx_pm_project_stage_order (project_id, deleted, stage_order),
    KEY idx_pm_project_stage_owner (owner_user_id, stage_status, deleted),
    CONSTRAINT fk_pm_project_stage_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_stage_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT chk_pm_project_stage_progress CHECK (completion_percent >= 0 AND completion_percent <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目阶段表';

CREATE TABLE IF NOT EXISTS pm_project_task (
    id BIGINT NOT NULL COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    stage_id BIGINT NOT NULL COMMENT '所属阶段ID',
    parent_task_id BIGINT NULL COMMENT '父任务ID',
    task_code VARCHAR(64) NOT NULL COMMENT '项目内任务编码',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL' COMMENT '任务类型',
    assignee_user_id BIGINT NULL COMMENT '任务负责人',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    task_status VARCHAR(32) NOT NULL DEFAULT 'TODO' COMMENT '任务状态',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    actual_start_date DATE NULL COMMENT '实际开始日期',
    actual_end_date DATE NULL COMMENT '实际结束日期',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '任务进度',
    output_desc VARCHAR(1000) NULL COMMENT '交付成果说明',
    risk_desc VARCHAR(1000) NULL COMMENT '风险说明',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '删除标识：0正常，1删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_task_code (project_id, task_code),
    KEY idx_pm_project_task_stage (stage_id, task_status, deleted, sort_no),
    KEY idx_pm_project_task_assignee (assignee_user_id, task_status, deleted),
    KEY idx_pm_project_task_parent (parent_task_id),
    CONSTRAINT fk_pm_project_task_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_task_stage FOREIGN KEY (stage_id) REFERENCES pm_project_stage (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_task_parent FOREIGN KEY (parent_task_id) REFERENCES pm_project_task (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_pm_project_task_assignee FOREIGN KEY (assignee_user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT chk_pm_project_task_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目任务表';

CREATE TABLE IF NOT EXISTS pm_project_member (
    id BIGINT NOT NULL COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '成员用户ID',
    member_role VARCHAR(32) NOT NULL COMMENT '成员角色：MANAGER/CORE/PARTICIPANT/EXPERT',
    responsibilities VARCHAR(1000) NULL COMMENT '职责说明',
    joined_date DATE NOT NULL COMMENT '加入日期',
    left_date DATE NULL COMMENT '退出日期',
    member_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '删除标识：0正常，1删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_member_role (project_id, user_id, member_role),
    KEY idx_pm_project_member_project (project_id, member_status, deleted),
    KEY idx_pm_project_member_user (user_id, member_status, deleted),
    CONSTRAINT fk_pm_project_member_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_member_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_pm_project_member_dates CHECK (left_date IS NULL OR joined_date <= left_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员表';

INSERT INTO sys_permission (
    id, permission_code, permission_name, permission_type,
    resource_path, http_method, module_code, status,
    created_time, updated_time, deleted
) VALUES
    (2001, 'project:lifecycle:list', '项目查询', 'API', '/projects/**', 'GET', 'project', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (2002, 'project:lifecycle:create', '项目新增', 'API', '/projects', 'POST', 'project', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (2003, 'project:lifecycle:update', '项目修改', 'API', '/projects/**', 'PUT', 'project', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (2004, 'project:lifecycle:delete', '项目删除', 'API', '/projects/**', 'DELETE', 'project', 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    resource_path = VALUES(resource_path),
    http_method = VALUES(http_method),
    updated_time = CURRENT_TIMESTAMP(3);

INSERT INTO sys_menu (
    id, parent_id, permission_id, menu_name, menu_type,
    route_name, route_path, component_path, icon, sort_no,
    visible, keep_alive, status, created_time, updated_time, deleted
) VALUES
    (200, NULL, NULL, '项目全生命周期', 'DIRECTORY', 'ProjectCenter', '/projects', NULL, 'Management', 20, 1, 0, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
    (210, 200, 2001, '项目库', 'MENU', 'ProjectList', '/projects', 'project/ProjectListView', 'List', 10, 1, 1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    permission_id = VALUES(permission_id),
    route_path = VALUES(route_path),
    component_path = VALUES(component_path),
    updated_time = CURRENT_TIMESTAMP(3);

INSERT INTO sys_role_permission (role_id, permission_id, created_time, updated_time, deleted)
SELECT 1, id, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_permission WHERE id BETWEEN 2001 AND 2004
ON DUPLICATE KEY UPDATE deleted = 0, updated_time = CURRENT_TIMESTAMP(3);

INSERT INTO sys_role_menu (role_id, menu_id, created_time, updated_time, deleted)
SELECT 1, id, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_menu WHERE id IN (200, 210)
ON DUPLICATE KEY UPDATE deleted = 0, updated_time = CURRENT_TIMESTAMP(3);
