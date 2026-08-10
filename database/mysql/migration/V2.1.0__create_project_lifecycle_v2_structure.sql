-- Lifecycle V2 structure. MySQL 8.4; requires the 05_project.sql baseline.
-- Rollback boundary: before any V2 application writes, DBA may drop these tables in reverse order.
USE enterprise_platform;

CREATE TABLE project_lifecycle_template (
    id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    project_type VARCHAR(50) NOT NULL,
    org_id BIGINT NULL,
    description VARCHAR(1000) NULL,
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
    UNIQUE KEY uk_lifecycle_template_code (template_code, delete_token),
    KEY idx_lifecycle_template_select (project_type, org_id, status, deleted),
    CONSTRAINT chk_lifecycle_template_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_lifecycle_template_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期模板头';

CREATE TABLE project_lifecycle_template_version (
    id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_name VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    content_checksum CHAR(64) NOT NULL,
    source_version_id BIGINT NULL,
    change_summary VARCHAR(1000) NULL,
    published_by VARCHAR(64) NULL,
    published_time DATETIME(3) NULL,
    frozen_by VARCHAR(64) NULL,
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
    UNIQUE KEY uk_lifecycle_template_version (template_id, version_no, delete_token),
    KEY idx_lifecycle_version_status (template_id, status, deleted),
    CONSTRAINT fk_lifecycle_version_template FOREIGN KEY (template_id)
        REFERENCES project_lifecycle_template(id),
    CONSTRAINT fk_lifecycle_version_source FOREIGN KEY (source_version_id)
        REFERENCES project_lifecycle_template_version(id),
    CONSTRAINT chk_lifecycle_version_no CHECK (version_no > 0),
    CONSTRAINT chk_lifecycle_version_status CHECK (status IN ('DRAFT', 'ACTIVE', 'FROZEN')),
    CONSTRAINT chk_lifecycle_version_dates CHECK (
        effective_from IS NULL OR effective_to IS NULL OR effective_from <= effective_to
    ),
    CONSTRAINT chk_lifecycle_version_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期模板版本';

CREATE TABLE project_lifecycle_template_active (
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    activated_by VARCHAR(64) NOT NULL,
    activated_time DATETIME(3) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (template_id),
    UNIQUE KEY uk_lifecycle_active_version (template_version_id),
    CONSTRAINT fk_lifecycle_active_template FOREIGN KEY (template_id)
        REFERENCES project_lifecycle_template(id),
    CONSTRAINT fk_lifecycle_active_version FOREIGN KEY (template_version_id)
        REFERENCES project_lifecycle_template_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期活动模板版本槽位';

CREATE TABLE project_lifecycle_stage_template (
    id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    stage_code VARCHAR(50) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    stage_order INT NOT NULL,
    required_flag SMALLINT NOT NULL DEFAULT 1,
    allow_skip SMALLINT NOT NULL DEFAULT 0,
    progress_weight DECIMAL(7,4) NOT NULL,
    planned_duration_days INT NULL,
    auto_start SMALLINT NOT NULL DEFAULT 0,
    approval_required SMALLINT NOT NULL DEFAULT 0,
    approval_scene_code VARCHAR(64) NULL,
    completion_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    description VARCHAR(1000) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lifecycle_stage_template_code
        (template_version_id, stage_code, delete_token),
    UNIQUE KEY uk_lifecycle_stage_template_order
        (template_version_id, stage_order, delete_token),
    KEY idx_lifecycle_stage_template_read
        (template_version_id, deleted, stage_order),
    CONSTRAINT fk_lifecycle_stage_template_version FOREIGN KEY (template_version_id)
        REFERENCES project_lifecycle_template_version(id),
    CONSTRAINT chk_lifecycle_stage_template_order CHECK (stage_order > 0),
    CONSTRAINT chk_lifecycle_stage_template_weight CHECK (
        progress_weight >= 0 AND progress_weight <= 100
    ),
    CONSTRAINT chk_lifecycle_stage_template_flags CHECK (
        required_flag IN (0, 1) AND allow_skip IN (0, 1)
        AND auto_start IN (0, 1) AND approval_required IN (0, 1)
    ),
    CONSTRAINT chk_lifecycle_stage_template_mode CHECK (
        completion_mode IN ('MANUAL', 'AUTO', 'HYBRID')
    ),
    CONSTRAINT chk_lifecycle_stage_template_approval CHECK (
        approval_required = 0 OR approval_scene_code IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期阶段模板';

CREATE TABLE project_lifecycle_stage_condition (
    id BIGINT NOT NULL,
    stage_template_id BIGINT NOT NULL,
    condition_type VARCHAR(20) NOT NULL,
    group_no INT NOT NULL DEFAULT 1,
    group_operator VARCHAR(8) NOT NULL DEFAULT 'AND',
    condition_code VARCHAR(64) NOT NULL,
    parameter_schema_version INT NOT NULL DEFAULT 1,
    parameters_text TEXT NULL,
    required_flag SMALLINT NOT NULL DEFAULT 1,
    failure_policy VARCHAR(10) NOT NULL DEFAULT 'BLOCK',
    sort_no INT NOT NULL DEFAULT 0,
    failure_message VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lifecycle_stage_condition_read
        (stage_template_id, condition_type, group_no, sort_no, deleted),
    CONSTRAINT fk_lifecycle_condition_stage FOREIGN KEY (stage_template_id)
        REFERENCES project_lifecycle_stage_template(id),
    CONSTRAINT chk_lifecycle_condition_type CHECK (
        condition_type IN ('ENTRY', 'COMPLETION', 'CLOSE')
    ),
    CONSTRAINT chk_lifecycle_condition_operator CHECK (group_operator IN ('AND', 'OR')),
    CONSTRAINT chk_lifecycle_condition_required CHECK (required_flag IN (0, 1)),
    CONSTRAINT chk_lifecycle_condition_failure CHECK (failure_policy IN ('BLOCK', 'WARN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期阶段条件';

CREATE TABLE project_lifecycle_instance (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    template_id BIGINT NULL,
    template_version_id BIGINT NULL,
    template_code_snapshot VARCHAR(64) NULL,
    template_name_snapshot VARCHAR(128) NULL,
    version_no_snapshot INT NULL,
    version_name_snapshot VARCHAR(100) NULL,
    template_checksum CHAR(64) NULL,
    snapshot_checksum CHAR(64) NULL,
    progress_policy_snapshot VARCHAR(32) NOT NULL,
    snapshot_status VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    initialized_by VARCHAR(64) NOT NULL,
    initialized_time DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lifecycle_instance_project (project_id, delete_token),
    KEY idx_lifecycle_instance_source (source_type, snapshot_status, deleted),
    KEY idx_lifecycle_instance_template (template_version_id, deleted),
    CONSTRAINT fk_lifecycle_instance_project FOREIGN KEY (project_id)
        REFERENCES project_info(id),
    CONSTRAINT fk_lifecycle_instance_template FOREIGN KEY (template_id)
        REFERENCES project_lifecycle_template(id),
    CONSTRAINT fk_lifecycle_instance_version FOREIGN KEY (template_version_id)
        REFERENCES project_lifecycle_template_version(id),
    CONSTRAINT chk_lifecycle_instance_source CHECK (
        (source_type = 'LEGACY' AND template_id IS NULL AND template_version_id IS NULL
            AND template_code_snapshot IS NULL AND template_name_snapshot IS NULL
            AND version_no_snapshot IS NULL AND template_checksum IS NULL)
        OR
        (source_type = 'TEMPLATE' AND template_id IS NOT NULL AND template_version_id IS NOT NULL
            AND template_code_snapshot IS NOT NULL AND template_name_snapshot IS NOT NULL
            AND version_no_snapshot IS NOT NULL AND template_checksum IS NOT NULL)
    ),
    CONSTRAINT chk_lifecycle_instance_policy CHECK (
        progress_policy_snapshot IN ('WEIGHTED', 'LEGACY_EQUAL')
    ),
    CONSTRAINT chk_lifecycle_instance_snapshot CHECK (
        snapshot_status IN ('BUILDING', 'READY', 'INVALID')
    ),
    CONSTRAINT chk_lifecycle_instance_status CHECK (
        status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'CLOSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期实例';

CREATE TABLE project_lifecycle_stage_snapshot (
    id BIGINT NOT NULL,
    lifecycle_instance_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_stage_template_id BIGINT NULL,
    stage_code VARCHAR(50) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    stage_order INT NOT NULL,
    progress_weight DECIMAL(7,4) NULL,
    weight_source VARCHAR(20) NOT NULL,
    required_flag SMALLINT NULL,
    allow_skip SMALLINT NULL,
    approval_required SMALLINT NULL,
    approval_scene_code VARCHAR(64) NULL,
    completion_mode VARCHAR(20) NULL,
    planned_duration_days INT NULL,
    condition_snapshot_status VARCHAR(20) NOT NULL,
    definition_checksum CHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lifecycle_stage_snapshot_code
        (lifecycle_instance_id, stage_code, delete_token),
    UNIQUE KEY uk_lifecycle_stage_snapshot_order
        (lifecycle_instance_id, stage_order, delete_token),
    UNIQUE KEY uk_lifecycle_stage_snapshot_project (id, project_id),
    KEY idx_lifecycle_stage_snapshot_read
        (project_id, lifecycle_instance_id, deleted, stage_order),
    CONSTRAINT fk_lifecycle_snapshot_instance FOREIGN KEY (lifecycle_instance_id)
        REFERENCES project_lifecycle_instance(id),
    CONSTRAINT fk_lifecycle_snapshot_project FOREIGN KEY (project_id)
        REFERENCES project_info(id),
    CONSTRAINT fk_lifecycle_snapshot_template_stage FOREIGN KEY (source_stage_template_id)
        REFERENCES project_lifecycle_stage_template(id),
    CONSTRAINT chk_lifecycle_snapshot_order CHECK (stage_order > 0),
    CONSTRAINT chk_lifecycle_snapshot_weight CHECK (
        progress_weight IS NULL OR (progress_weight >= 0 AND progress_weight <= 100)
    ),
    CONSTRAINT chk_lifecycle_snapshot_source CHECK (
        weight_source IN ('TEMPLATE', 'UNCONFIRMED')
    ),
    CONSTRAINT chk_lifecycle_snapshot_condition CHECK (
        condition_snapshot_status IN ('READY', 'NONE', 'UNAVAILABLE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期阶段定义快照';

CREATE TABLE project_lifecycle_condition_snapshot (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    lifecycle_instance_id BIGINT NOT NULL,
    stage_snapshot_id BIGINT NOT NULL,
    source_condition_id BIGINT NULL,
    condition_type VARCHAR(20) NOT NULL,
    group_no INT NOT NULL,
    group_operator VARCHAR(8) NOT NULL,
    condition_code VARCHAR(64) NOT NULL,
    parameter_schema_version INT NOT NULL,
    parameters_text TEXT NULL,
    required_flag SMALLINT NOT NULL,
    failure_policy VARCHAR(10) NOT NULL,
    sort_no INT NOT NULL,
    failure_message VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lifecycle_condition_snapshot_read
        (project_id, lifecycle_instance_id, stage_snapshot_id, condition_type, group_no, sort_no, deleted),
    CONSTRAINT fk_lifecycle_condition_snapshot_project FOREIGN KEY (project_id)
        REFERENCES project_info(id),
    CONSTRAINT fk_lifecycle_condition_snapshot_instance FOREIGN KEY (lifecycle_instance_id)
        REFERENCES project_lifecycle_instance(id),
    CONSTRAINT fk_lifecycle_condition_snapshot_stage FOREIGN KEY (stage_snapshot_id)
        REFERENCES project_lifecycle_stage_snapshot(id),
    CONSTRAINT fk_lifecycle_condition_snapshot_source FOREIGN KEY (source_condition_id)
        REFERENCES project_lifecycle_stage_condition(id),
    CONSTRAINT chk_lifecycle_condition_snapshot_type CHECK (
        condition_type IN ('ENTRY', 'COMPLETION', 'CLOSE')
    ),
    CONSTRAINT chk_lifecycle_condition_snapshot_operator CHECK (group_operator IN ('AND', 'OR')),
    CONSTRAINT chk_lifecycle_condition_snapshot_required CHECK (required_flag IN (0, 1)),
    CONSTRAINT chk_lifecycle_condition_snapshot_failure CHECK (failure_policy IN ('BLOCK', 'WARN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目生命周期条件快照';
