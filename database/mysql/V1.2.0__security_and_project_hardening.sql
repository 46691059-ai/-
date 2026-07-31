-- Security and project lifecycle hardening
-- Target: MySQL 8.x

SET NAMES utf8mb4;

ALTER TABLE sys_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本，递增后使历史令牌失效'
        AFTER status,
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_sys_user_username,
    DROP INDEX uk_sys_user_employee_no,
    ADD UNIQUE KEY uk_sys_user_username (username, delete_token),
    ADD UNIQUE KEY uk_sys_user_employee_no (employee_no, delete_token);

ALTER TABLE sys_org
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_sys_org_code,
    ADD UNIQUE KEY uk_sys_org_code (org_code, delete_token);

ALTER TABLE sys_role
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_sys_role_code,
    ADD UNIQUE KEY uk_sys_role_code (role_code, delete_token);

ALTER TABLE sys_permission
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_sys_permission_code,
    ADD UNIQUE KEY uk_sys_permission_code (permission_code, delete_token);

ALTER TABLE pm_project
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_pm_project_code,
    ADD UNIQUE KEY uk_pm_project_code (project_code, delete_token),
    ADD KEY idx_pm_project_scope_list (org_id, deleted, project_status, created_time),
    ADD CONSTRAINT chk_pm_project_amount CHECK (
        investment_amount >= 0 AND expected_income >= 0 AND actual_income >= 0
    );

ALTER TABLE pm_project_stage
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_pm_project_stage_code,
    ADD UNIQUE KEY uk_pm_project_stage_code (project_id, stage_code, delete_token),
    ADD UNIQUE KEY uk_pm_project_stage_id_project (id, project_id);

ALTER TABLE pm_project_task
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_pm_project_task_code,
    ADD UNIQUE KEY uk_pm_project_task_code (project_id, task_code, delete_token),
    ADD UNIQUE KEY uk_pm_project_task_id_project (id, project_id),
    ADD KEY idx_pm_project_task_list (project_id, deleted, sort_no, created_time),
    DROP FOREIGN KEY fk_pm_project_task_stage,
    DROP FOREIGN KEY fk_pm_project_task_parent,
    ADD CONSTRAINT fk_pm_project_task_stage_project
        FOREIGN KEY (stage_id, project_id)
        REFERENCES pm_project_stage (id, project_id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT fk_pm_project_task_parent_project
        FOREIGN KEY (parent_task_id, project_id)
        REFERENCES pm_project_task (id, project_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE pm_project_member
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除唯一标识' AFTER deleted,
    DROP INDEX uk_pm_project_member_role,
    ADD UNIQUE KEY uk_pm_project_member_role (
        project_id, user_id, member_role, delete_token
    ),
    ADD KEY idx_pm_project_member_list (project_id, deleted, created_time);
