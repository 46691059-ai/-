-- ============================================
-- 系统基础域：用户、角色、权限、组织、字典、日志、文件、消息
-- 主键由应用雪花算法生成，不依赖 MySQL AUTO_INCREMENT。
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE sys_org (
    id BIGINT NOT NULL COMMENT '组织ID',
    org_code VARCHAR(50) NOT NULL COMMENT '组织编码',
    org_name VARCHAR(100) NOT NULL COMMENT '组织名称',
    org_type VARCHAR(30) NOT NULL COMMENT 'COMPANY/DEPARTMENT/PARTY_ORG/PROJECT_TEAM',
    parent_id BIGINT NULL COMMENT '上级组织ID，根组织为空',
    leader_id BIGINT NULL COMMENT '负责人员工ID，员工表创建后补充外键',
    tree_path VARCHAR(1000) NOT NULL DEFAULT '/' COMMENT '祖先路径',
    tree_level INT NOT NULL DEFAULT 1 COMMENT '树层级',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status SMALLINT NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构主数据表';

CREATE TABLE sys_role (
    id BIGINT NOT NULL COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(200) NULL COMMENT '角色说明',
    data_scope_type VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL COMMENT '权限ID',
    permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限资源表';

CREATE TABLE sys_menu (
    id BIGINT NOT NULL COMMENT '菜单ID',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT NULL COMMENT '父菜单ID，根菜单为空',
    menu_type VARCHAR(20) NOT NULL COMMENT 'M目录 C菜单 B按钮',
    path VARCHAR(200) NULL,
    component VARCHAR(200) NULL,
    permission VARCHAR(200) NULL COMMENT '权限标识',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单权限表';

CREATE TABLE sys_user (
    id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT '仅保存BCrypt或Argon2摘要',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    phone VARCHAR(20) NULL COMMENT '手机号',
    email VARCHAR(100) NULL COMMENT '邮箱',
    org_id BIGINT NOT NULL COMMENT '所属组织ID',
    employee_id BIGINT NULL COMMENT '关联员工ID，员工表创建后补充外键',
    status SMALLINT NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    token_version INT NOT NULL DEFAULT 0 COMMENT 'JWT令牌版本',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关系表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色组织数据范围表';

CREATE TABLE sys_dict (
    id BIGINT NOT NULL COMMENT '字典ID',
    dict_type VARCHAR(50) NOT NULL COMMENT '字典类型',
    dict_label VARCHAR(100) NOT NULL COMMENT '显示名称',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典值',
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
    UNIQUE KEY uk_sys_dict_value (dict_type, dict_value, delete_token),
    KEY idx_sys_dict_type_sort (dict_type, status, deleted, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统数据字典表';

CREATE TABLE sys_log (
    id BIGINT NOT NULL,
    user_id BIGINT NULL,
    operation VARCHAR(100) NOT NULL,
    request_url VARCHAR(500) NULL,
    request_method VARCHAR(20) NULL,
    ip VARCHAR(64) NULL,
    result VARCHAR(20) NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

CREATE TABLE sys_file (
    id BIGINT NOT NULL COMMENT '文件ID',
    file_name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL COMMENT '对象存储键或受控相对路径',
    file_type VARCHAR(50) NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_hash VARCHAR(128) NULL COMMENT '文件完整性摘要',
    storage_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    business_type VARCHAR(50) NULL COMMENT '业务类型',
    business_id BIGINT NULL COMMENT '业务ID，逻辑引用',
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
    KEY idx_sys_file_business (business_type, business_id, deleted),
    KEY idx_sys_file_hash (file_hash, deleted),
    KEY idx_sys_file_create (create_time, deleted),
    CONSTRAINT chk_sys_file_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一文件管理表';

CREATE TABLE sys_message (
    id BIGINT NOT NULL COMMENT '消息ID',
    receiver_id BIGINT NOT NULL COMMENT '接收用户ID',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    read_status SMALLINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sys_message_receiver (receiver_id, read_status, deleted, create_time),
    KEY idx_sys_message_type (message_type, create_time, deleted),
    CONSTRAINT fk_sys_message_receiver FOREIGN KEY (receiver_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息中心表';

SET FOREIGN_KEY_CHECKS = 1;
