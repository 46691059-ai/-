-- ============================================
-- 国企数字化治理与经营赋能平台
-- 数据资产管理数据库完整 SQL
-- 数据来源 -> 数据资源 -> 治理/质量 -> 数据资产 -> 数据产品 -> 授权/交易 -> 收益
-- 主键由应用雪花算法生成，兼顾 MySQL、达梦和人大金仓迁移
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE data_source (
    id BIGINT NOT NULL COMMENT '数据来源ID',
    source_code VARCHAR(50) NOT NULL COMMENT '来源编码',
    source_name VARCHAR(200) NOT NULL COMMENT '来源名称',
    source_type VARCHAR(50) NOT NULL COMMENT 'PUBLIC_DATA/ENTERPRISE_DATA/IOT_DATA/TRANSACTION_DATA',
    provider_unit VARCHAR(200) NOT NULL COMMENT '数据提供单位',
    contact_person VARCHAR(50) NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话，敏感字段应加密存储',
    authorization_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_source_code (source_code, delete_token),
    KEY idx_data_source_name (source_name, deleted),
    KEY idx_data_source_type (source_type, authorization_status, status, deleted),
    CONSTRAINT chk_data_source_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据来源管理表';

CREATE TABLE data_security_level (
    id BIGINT NOT NULL COMMENT '安全等级ID',
    level_code VARCHAR(20) NOT NULL COMMENT 'L1/L2/L3/L4',
    level_name VARCHAR(50) NOT NULL COMMENT '等级名称',
    description VARCHAR(500) NULL COMMENT '等级说明',
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
    UNIQUE KEY uk_data_security_level_code (level_code),
    CONSTRAINT chk_data_security_level_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据安全等级表';

CREATE TABLE data_resource (
    id BIGINT NOT NULL COMMENT '数据资源ID',
    resource_code VARCHAR(50) NOT NULL COMMENT '资源编码',
    resource_name VARCHAR(200) NOT NULL COMMENT '资源名称',
    source_id BIGINT NOT NULL COMMENT '数据来源ID',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型',
    business_domain VARCHAR(100) NULL COMMENT '业务领域',
    data_owner VARCHAR(200) NOT NULL COMMENT '数据权属单位',
    responsible_person BIGINT NOT NULL COMMENT '责任人员工ID',
    update_frequency VARCHAR(50) NULL COMMENT '更新频率',
    data_volume VARCHAR(100) NULL COMMENT '数据规模描述',
    security_level VARCHAR(20) NOT NULL COMMENT '数据安全等级编码',
    sharing_level VARCHAR(20) NULL COMMENT '共享等级',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_resource_code (resource_code, delete_token),
    KEY idx_data_resource_source (source_id, resource_type, deleted),
    KEY idx_data_resource_domain (business_domain, status, deleted),
    KEY idx_data_resource_security (security_level, sharing_level, status, deleted),
    KEY idx_data_resource_person (responsible_person, status, deleted),
    CONSTRAINT fk_data_resource_source FOREIGN KEY (source_id) REFERENCES data_source(id),
    CONSTRAINT fk_data_resource_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT fk_data_resource_security FOREIGN KEY (security_level)
        REFERENCES data_security_level(level_code),
    CONSTRAINT chk_data_resource_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资源目录表';

CREATE TABLE data_resource_field (
    id BIGINT NOT NULL COMMENT '资源字段ID',
    resource_id BIGINT NOT NULL COMMENT '数据资源ID',
    field_name VARCHAR(100) NOT NULL COMMENT '字段名称',
    field_code VARCHAR(100) NOT NULL COMMENT '字段编码',
    data_type VARCHAR(50) NOT NULL COMMENT '数据类型',
    field_length VARCHAR(20) NULL COMMENT '字段长度，规范原length字段',
    is_sensitive SMALLINT NOT NULL DEFAULT 0 COMMENT '是否敏感：1是，0否',
    description VARCHAR(500) NULL COMMENT '字段说明',
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
    UNIQUE KEY uk_data_resource_field_code (resource_id, field_code, delete_token),
    KEY idx_data_resource_field_sort (resource_id, deleted, sort_no),
    KEY idx_data_resource_field_sensitive (resource_id, is_sensitive, deleted),
    CONSTRAINT fk_data_resource_field_resource FOREIGN KEY (resource_id)
        REFERENCES data_resource(id),
    CONSTRAINT chk_data_resource_field_sensitive CHECK (is_sensitive IN (0, 1)),
    CONSTRAINT chk_data_resource_field_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资源字段目录表';

CREATE TABLE data_quality (
    id BIGINT NOT NULL COMMENT '数据质量评价ID',
    resource_id BIGINT NOT NULL COMMENT '数据资源ID',
    completeness_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '完整性评分',
    accuracy_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '准确性评分',
    timeliness_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '及时性评分',
    consistency_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '一致性评分',
    security_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '安全性评分',
    overall_score DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '综合评分',
    evaluation_date DATE NOT NULL COMMENT '评价日期',
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
    UNIQUE KEY uk_data_quality_date (resource_id, evaluation_date, delete_token),
    KEY idx_data_quality_score (overall_score, evaluation_date, deleted),
    KEY idx_data_quality_evaluator (evaluator, evaluation_date, deleted),
    CONSTRAINT fk_data_quality_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT fk_data_quality_evaluator FOREIGN KEY (evaluator) REFERENCES hr_employee(id),
    CONSTRAINT chk_data_quality_scores CHECK (
        completeness_score BETWEEN 0 AND 100
        AND accuracy_score BETWEEN 0 AND 100
        AND timeliness_score BETWEEN 0 AND 100
        AND consistency_score BETWEEN 0 AND 100
        AND security_score BETWEEN 0 AND 100
        AND overall_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_data_quality_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量评价表';

CREATE TABLE data_governance_task (
    id BIGINT NOT NULL COMMENT '治理任务ID',
    resource_id BIGINT NOT NULL COMMENT '数据资源ID',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL COMMENT 'CLEAN/DEDUPLICATE/STANDARDIZE/MASK/FUSE',
    responsible_person BIGINT NOT NULL COMMENT '责任人员工ID',
    start_date DATE NULL COMMENT '开始日期',
    end_date DATE NULL COMMENT '结束日期',
    result TEXT NULL COMMENT '治理结果',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_governance_resource (resource_id, status, deleted),
    KEY idx_data_governance_person (responsible_person, status, end_date, deleted),
    KEY idx_data_governance_due (status, end_date, deleted),
    CONSTRAINT fk_data_governance_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT fk_data_governance_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_data_governance_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    ),
    CONSTRAINT chk_data_governance_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据治理任务表';

CREATE TABLE data_asset (
    id BIGINT NOT NULL COMMENT '数据资产ID',
    asset_code VARCHAR(50) NOT NULL COMMENT '资产编号',
    asset_name VARCHAR(200) NOT NULL COMMENT '资产名称',
    resource_id BIGINT NOT NULL COMMENT '数据资源ID',
    asset_type VARCHAR(50) NOT NULL COMMENT '资产类型',
    ownership VARCHAR(200) NOT NULL COMMENT '权属主体',
    application_scene TEXT NULL COMMENT '应用场景',
    value_level VARCHAR(20) NULL COMMENT '价值等级',
    evaluation_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '最新评估价值',
    evaluation_method VARCHAR(100) NULL COMMENT '最新评估方法',
    evaluation_date DATE NULL COMMENT '最新评估日期',
    status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_asset_code (asset_code, delete_token),
    KEY idx_data_asset_resource (resource_id, status, deleted),
    KEY idx_data_asset_value (value_level, status, deleted),
    KEY idx_data_asset_evaluation (evaluation_date, status, deleted),
    CONSTRAINT fk_data_asset_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT chk_data_asset_amount CHECK (evaluation_amount >= 0),
    CONSTRAINT chk_data_asset_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资产登记表';

CREATE TABLE data_asset_evaluation (
    id BIGINT NOT NULL COMMENT '资产评估记录ID',
    asset_id BIGINT NOT NULL COMMENT '数据资产ID',
    evaluation_org VARCHAR(200) NOT NULL COMMENT '评估机构',
    evaluation_method VARCHAR(100) NOT NULL COMMENT 'COST/INCOME/MARKET',
    market_value DECIMAL(18,2) NULL COMMENT '市场法评估值',
    cost_value DECIMAL(18,2) NULL COMMENT '成本法评估值',
    income_value DECIMAL(18,2) NULL COMMENT '收益法评估值',
    evaluation_report VARCHAR(500) NULL COMMENT '评估报告地址；正式文件建议关联sys_file',
    evaluation_date DATE NOT NULL COMMENT '评估日期',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_asset_evaluation (asset_id, evaluation_date, evaluation_method, delete_token),
    KEY idx_data_asset_evaluation_date (evaluation_date, deleted),
    CONSTRAINT fk_data_asset_evaluation_asset FOREIGN KEY (asset_id) REFERENCES data_asset(id),
    CONSTRAINT chk_data_asset_evaluation_amount CHECK (
        (market_value IS NULL OR market_value >= 0)
        AND (cost_value IS NULL OR cost_value >= 0)
        AND (income_value IS NULL OR income_value >= 0)
    ),
    CONSTRAINT chk_data_asset_evaluation_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资产价值评估记录表';

CREATE TABLE data_product (
    id BIGINT NOT NULL COMMENT '数据产品ID',
    product_code VARCHAR(50) NOT NULL COMMENT '产品编码',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    asset_id BIGINT NOT NULL COMMENT '数据资产ID',
    product_type VARCHAR(50) NOT NULL COMMENT '产品类型',
    service_object VARCHAR(200) NULL COMMENT '服务对象',
    application_scene TEXT NULL COMMENT '应用场景',
    service_mode VARCHAR(50) NOT NULL COMMENT '服务模式',
    price DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '产品价格',
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
    UNIQUE KEY uk_data_product_code (product_code, delete_token),
    KEY idx_data_product_asset (asset_id, status, deleted),
    KEY idx_data_product_type (product_type, status, deleted),
    CONSTRAINT fk_data_product_asset FOREIGN KEY (asset_id) REFERENCES data_asset(id),
    CONSTRAINT chk_data_product_price CHECK (price >= 0),
    CONSTRAINT chk_data_product_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据产品表';

CREATE TABLE data_service_api (
    id BIGINT NOT NULL COMMENT '数据服务接口ID',
    product_id BIGINT NOT NULL COMMENT '数据产品ID',
    api_name VARCHAR(200) NOT NULL COMMENT '接口名称',
    api_url VARCHAR(500) NOT NULL COMMENT '接口地址',
    request_method VARCHAR(20) NOT NULL COMMENT 'GET/POST等请求方式',
    security_method VARCHAR(50) NOT NULL COMMENT '认证与安全方式',
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
    UNIQUE KEY uk_data_service_api_name (product_id, api_name, delete_token),
    KEY idx_data_service_api_status (product_id, status, deleted),
    CONSTRAINT fk_data_service_api_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT chk_data_service_api_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据服务接口表';

CREATE TABLE data_authorization (
    id BIGINT NOT NULL COMMENT '数据授权ID',
    product_id BIGINT NOT NULL COMMENT '数据产品ID',
    customer_name VARCHAR(200) NOT NULL COMMENT '被授权客户名称',
    authorization_type VARCHAR(50) NOT NULL COMMENT 'SHARE/SERVICE/LICENSE/TRADE',
    purpose TEXT NOT NULL COMMENT '使用目的',
    start_date DATE NOT NULL COMMENT '授权开始日期',
    end_date DATE NULL COMMENT '授权结束日期',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    contract_id BIGINT NULL COMMENT '关联合同ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_authorization_product (product_id, approval_status, deleted),
    KEY idx_data_authorization_expiry (end_date, approval_status, deleted),
    KEY idx_data_authorization_contract (contract_id, deleted),
    CONSTRAINT fk_data_authorization_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT fk_data_authorization_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT chk_data_authorization_dates CHECK (end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_data_authorization_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据授权运营表';

CREATE TABLE data_trade_order (
    id BIGINT NOT NULL COMMENT '数据交易订单ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单编号',
    product_id BIGINT NOT NULL COMMENT '数据产品ID',
    buyer_name VARCHAR(200) NOT NULL COMMENT '购买方名称',
    trade_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '交易金额',
    trade_date DATE NOT NULL COMMENT '交易日期',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    contract_id BIGINT NULL COMMENT '关联合同ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_trade_order_no (order_no, delete_token),
    KEY idx_data_trade_order_product (product_id, trade_date, status, deleted),
    KEY idx_data_trade_order_contract (contract_id, deleted),
    CONSTRAINT fk_data_trade_order_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT fk_data_trade_order_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT chk_data_trade_order_amount CHECK (trade_amount >= 0),
    CONSTRAINT chk_data_trade_order_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据交易订单表';

CREATE TABLE data_income (
    id BIGINT NOT NULL COMMENT '数据收益ID',
    product_id BIGINT NOT NULL COMMENT '数据产品ID',
    authorization_id BIGINT NULL COMMENT '数据授权ID',
    trade_order_id BIGINT NULL COMMENT '数据交易订单ID',
    contract_id BIGINT NULL COMMENT '关联合同ID',
    income_type VARCHAR(50) NOT NULL COMMENT '收益类型',
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收益金额',
    income_date DATE NOT NULL COMMENT '收益日期',
    customer_name VARCHAR(200) NOT NULL COMMENT '客户名称',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_income_product (product_id, income_date, deleted),
    KEY idx_data_income_authorization (authorization_id, deleted),
    KEY idx_data_income_trade (trade_order_id, deleted),
    KEY idx_data_income_contract (contract_id, deleted),
    CONSTRAINT fk_data_income_product FOREIGN KEY (product_id) REFERENCES data_product(id),
    CONSTRAINT fk_data_income_authorization FOREIGN KEY (authorization_id)
        REFERENCES data_authorization(id),
    CONSTRAINT fk_data_income_trade FOREIGN KEY (trade_order_id) REFERENCES data_trade_order(id),
    CONSTRAINT fk_data_income_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT chk_data_income_amount CHECK (income_amount >= 0),
    CONSTRAINT chk_data_income_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据资产收益记录表';

CREATE TABLE data_access_log (
    id BIGINT NOT NULL COMMENT '数据访问日志ID',
    user_id BIGINT NOT NULL COMMENT '访问用户ID',
    resource_id BIGINT NOT NULL COMMENT '数据资源ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '访问操作类型',
    access_time DATETIME(3) NOT NULL COMMENT '访问时间',
    ip VARCHAR(50) NULL COMMENT '来源IP',
    result VARCHAR(20) NOT NULL COMMENT '访问结果',
    trace_id VARCHAR(64) NULL COMMENT '调用链追踪ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_data_access_user_time (user_id, access_time),
    KEY idx_data_access_resource_time (resource_id, access_time),
    KEY idx_data_access_result_time (result, access_time),
    KEY idx_data_access_trace (trace_id),
    CONSTRAINT fk_data_access_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_data_access_resource FOREIGN KEY (resource_id) REFERENCES data_resource(id),
    CONSTRAINT chk_data_access_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据访问安全审计日志表';

SET FOREIGN_KEY_CHECKS = 1;
