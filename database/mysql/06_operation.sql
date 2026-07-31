-- ============================================
-- 国企数字化治理与经营赋能平台
-- 经营管理数据库完整 SQL
-- 项目 -> 合同 -> 收入/成本 -> 利润 -> 应收 -> 回款
-- 主键由应用雪花算法生成，兼顾 MySQL、达梦和人大金仓迁移
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE operation_customer (
    id BIGINT NOT NULL COMMENT '客户ID',
    customer_no VARCHAR(50) NOT NULL COMMENT '客户编号',
    customer_name VARCHAR(200) NOT NULL COMMENT '客户名称',
    customer_type VARCHAR(50) NOT NULL COMMENT 'GOVERNMENT/SOE/PRIVATE/INSTITUTION/OTHER',
    credit_code VARCHAR(100) NULL COMMENT '统一社会信用代码',
    contact_person VARCHAR(50) NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话，敏感字段应加密存储',
    address VARCHAR(300) NULL COMMENT '联系地址',
    industry VARCHAR(100) NULL COMMENT '所属行业',
    customer_level VARCHAR(20) NULL COMMENT '客户等级，规范原level字段',
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
    UNIQUE KEY uk_operation_customer_no (customer_no, delete_token),
    UNIQUE KEY uk_operation_customer_credit (credit_code, delete_token),
    KEY idx_operation_customer_name (customer_name, deleted),
    KEY idx_operation_customer_type (customer_type, status, deleted),
    CONSTRAINT chk_operation_customer_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户信息表';

CREATE TABLE operation_supplier (
    id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_no VARCHAR(50) NOT NULL COMMENT '供应商编号',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    credit_code VARCHAR(100) NULL COMMENT '统一社会信用代码',
    supplier_type VARCHAR(50) NOT NULL COMMENT '采购/外包/设备等供应商分类',
    contact_person VARCHAR(50) NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话，敏感字段应加密存储',
    qualification TEXT NULL COMMENT '资质说明',
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
    UNIQUE KEY uk_operation_supplier_no (supplier_no, delete_token),
    UNIQUE KEY uk_operation_supplier_credit (credit_code, delete_token),
    KEY idx_operation_supplier_name (supplier_name, deleted),
    KEY idx_operation_supplier_type (supplier_type, status, deleted),
    CONSTRAINT chk_operation_supplier_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商管理表';

CREATE TABLE operation_contract (
    id BIGINT NOT NULL COMMENT '合同ID',
    contract_no VARCHAR(50) NOT NULL COMMENT '合同编号',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    contract_type VARCHAR(50) NOT NULL COMMENT 'SALES/SERVICE/PURCHASE/INVESTMENT/COOPERATION',
    project_id BIGINT NULL COMMENT '关联项目ID',
    customer_id BIGINT NULL COMMENT '关联客户ID',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同金额',
    sign_date DATE NULL COMMENT '签订日期',
    start_date DATE NULL COMMENT '合同开始日期',
    end_date DATE NULL COMMENT '合同结束日期',
    payment_method TEXT NULL COMMENT '付款方式',
    responsible_person BIGINT NULL COMMENT '合同负责人，关联员工',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    attachment VARCHAR(500) NULL COMMENT '附件地址；正式文件建议关联sys_file',
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
    KEY idx_operation_contract_customer (customer_id, status, deleted),
    KEY idx_operation_contract_person (responsible_person, status, deleted),
    KEY idx_operation_contract_dates (start_date, end_date, deleted),
    CONSTRAINT fk_operation_contract_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_operation_contract_customer FOREIGN KEY (customer_id) REFERENCES operation_customer(id),
    CONSTRAINT fk_operation_contract_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_operation_contract_amount CHECK (amount >= 0),
    CONSTRAINT chk_operation_contract_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    ),
    CONSTRAINT chk_operation_contract_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同主表';

CREATE TABLE operation_contract_approval (
    id BIGINT NOT NULL COMMENT '审批记录ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    node_name VARCHAR(100) NOT NULL COMMENT '审批节点名称',
    approver_id BIGINT NOT NULL COMMENT '审批人员工ID',
    approval_result VARCHAR(20) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/RETURNED',
    approval_comment TEXT NULL COMMENT '审批意见',
    approval_time DATETIME(3) NULL COMMENT '审批时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_operation_contract_approval_contract (contract_id, deleted, create_time),
    KEY idx_operation_contract_approval_user (approver_id, approval_result, deleted),
    CONSTRAINT fk_operation_contract_approval_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT fk_operation_contract_approval_user FOREIGN KEY (approver_id)
        REFERENCES hr_employee(id),
    CONSTRAINT chk_operation_contract_approval_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同审批记录表';

CREATE TABLE operation_contract_payment (
    id BIGINT NOT NULL COMMENT '付款节点ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    payment_name VARCHAR(200) NOT NULL COMMENT '付款节点名称',
    payment_ratio DECIMAL(7,4) NULL COMMENT '付款比例，百分比',
    payment_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '付款金额',
    plan_date DATE NOT NULL COMMENT '计划付款日期',
    actual_date DATE NULL COMMENT '实际付款日期',
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
    KEY idx_operation_contract_payment_contract (contract_id, status, plan_date, deleted),
    KEY idx_operation_contract_payment_due (status, plan_date, deleted),
    CONSTRAINT fk_operation_contract_payment_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT chk_operation_contract_payment_ratio CHECK (
        payment_ratio IS NULL OR payment_ratio BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_operation_contract_payment_amount CHECK (payment_amount >= 0),
    CONSTRAINT chk_operation_contract_payment_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同付款计划表';

CREATE TABLE operation_contract_change (
    id BIGINT NOT NULL COMMENT '合同变更ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    change_type VARCHAR(50) NOT NULL COMMENT '变更类型',
    change_content TEXT NOT NULL COMMENT '变更内容',
    before_amount DECIMAL(18,2) NULL COMMENT '变更前金额',
    after_amount DECIMAL(18,2) NULL COMMENT '变更后金额',
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
    KEY idx_operation_contract_change_contract (contract_id, approval_status, deleted, create_time),
    CONSTRAINT fk_operation_contract_change_contract FOREIGN KEY (contract_id)
        REFERENCES operation_contract(id),
    CONSTRAINT chk_operation_contract_change_amount CHECK (
        (before_amount IS NULL OR before_amount >= 0)
        AND (after_amount IS NULL OR after_amount >= 0)
    ),
    CONSTRAINT chk_operation_contract_change_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更记录表';

CREATE TABLE operation_income (
    id BIGINT NOT NULL COMMENT '收入记录ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    income_type VARCHAR(50) NOT NULL COMMENT 'CONTRACT/SERVICE/OPERATION/INVESTMENT/DATA_PRODUCT',
    income_amount DECIMAL(18,2) NOT NULL COMMENT '确认收入金额',
    income_date DATE NOT NULL COMMENT '收入确认日期',
    confirm_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confirm_person BIGINT NULL COMMENT '确认人员工ID',
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
    KEY idx_operation_income_contract (contract_id, confirm_status, deleted),
    KEY idx_operation_income_confirm (confirm_person, confirm_status, deleted),
    CONSTRAINT fk_operation_income_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_operation_income_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT fk_operation_income_person FOREIGN KEY (confirm_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_operation_income_amount CHECK (income_amount >= 0),
    CONSTRAINT chk_operation_income_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营收入确认表';

CREATE TABLE operation_receivable (
    id BIGINT NOT NULL COMMENT '应收账款ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    receivable_amount DECIMAL(18,2) NOT NULL COMMENT '应收金额',
    received_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已收金额',
    remaining_amount DECIMAL(18,2) NOT NULL COMMENT '剩余金额，由应用按应收减已收维护',
    due_date DATE NOT NULL COMMENT '到期日期',
    actual_date DATE NULL COMMENT '结清日期',
    aging_days INT NOT NULL DEFAULT 0 COMMENT '账龄天数，由定时任务更新',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    responsible_person BIGINT NULL COMMENT '催收负责人员工ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_operation_receivable_contract (contract_id, status, deleted),
    KEY idx_operation_receivable_customer (customer_id, status, due_date, deleted),
    KEY idx_operation_receivable_due (status, due_date, deleted),
    KEY idx_operation_receivable_person (responsible_person, status, deleted),
    CONSTRAINT fk_operation_receivable_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT fk_operation_receivable_customer FOREIGN KEY (customer_id) REFERENCES operation_customer(id),
    CONSTRAINT fk_operation_receivable_person FOREIGN KEY (responsible_person) REFERENCES hr_employee(id),
    CONSTRAINT chk_operation_receivable_amount CHECK (
        receivable_amount >= 0 AND received_amount >= 0
        AND received_amount <= receivable_amount
        AND remaining_amount = receivable_amount - received_amount
    ),
    CONSTRAINT chk_operation_receivable_aging CHECK (aging_days >= 0),
    CONSTRAINT chk_operation_receivable_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收账款管理表';

CREATE TABLE operation_collection (
    id BIGINT NOT NULL COMMENT '回款记录ID',
    receivable_id BIGINT NOT NULL COMMENT '应收账款ID',
    collection_amount DECIMAL(18,2) NOT NULL COMMENT '回款金额',
    collection_date DATE NOT NULL COMMENT '回款日期',
    collection_method VARCHAR(50) NULL COMMENT '回款方式',
    bank_reference VARCHAR(100) NULL COMMENT '银行流水号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_collection_bank (bank_reference, delete_token),
    KEY idx_operation_collection_receivable (receivable_id, collection_date, deleted),
    KEY idx_operation_collection_date (collection_date, deleted),
    CONSTRAINT fk_operation_collection_receivable FOREIGN KEY (receivable_id)
        REFERENCES operation_receivable(id),
    CONSTRAINT chk_operation_collection_amount CHECK (collection_amount > 0),
    CONSTRAINT chk_operation_collection_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款记录表';

CREATE TABLE operation_cost (
    id BIGINT NOT NULL COMMENT '成本记录ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    cost_type VARCHAR(50) NOT NULL COMMENT 'LABOR/PURCHASE/OUTSOURCE/EQUIPMENT/OPERATION/MANAGEMENT',
    cost_name VARCHAR(200) NOT NULL COMMENT '成本名称',
    supplier_id BIGINT NULL COMMENT '供应商ID',
    amount DECIMAL(18,2) NOT NULL COMMENT '成本金额',
    cost_date DATE NOT NULL COMMENT '成本发生日期',
    source_type VARCHAR(50) NULL COMMENT '成本来源类型',
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
    KEY idx_operation_cost_contract (contract_id, cost_date, deleted),
    KEY idx_operation_cost_supplier (supplier_id, cost_date, deleted),
    KEY idx_operation_cost_type (cost_type, cost_date, deleted),
    CONSTRAINT fk_operation_cost_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT fk_operation_cost_contract FOREIGN KEY (contract_id) REFERENCES operation_contract(id),
    CONSTRAINT fk_operation_cost_supplier FOREIGN KEY (supplier_id) REFERENCES operation_supplier(id),
    CONSTRAINT chk_operation_cost_amount CHECK (amount >= 0),
    CONSTRAINT chk_operation_cost_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营成本归集表';

CREATE TABLE operation_cost_budget (
    id BIGINT NOT NULL COMMENT '成本预算ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    cost_type VARCHAR(50) NOT NULL COMMENT '成本类型',
    budget_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预算金额',
    actual_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际金额',
    remaining_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '剩余预算，由应用维护',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_cost_budget (project_id, cost_type, delete_token),
    KEY idx_operation_cost_budget_project (project_id, deleted),
    CONSTRAINT fk_operation_cost_budget_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_operation_cost_budget_amount CHECK (
        budget_amount >= 0 AND actual_amount >= 0
        AND remaining_amount = budget_amount - actual_amount
    ),
    CONSTRAINT chk_operation_cost_budget_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成本预算表';

CREATE TABLE operation_profit_analysis (
    id BIGINT NOT NULL COMMENT '利润分析ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同金额',
    income_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收入金额',
    cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '成本金额',
    profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '利润金额',
    profit_rate DECIMAL(9,4) NULL COMMENT '利润率，百分比',
    analysis_date DATE NOT NULL COMMENT '分析日期',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_profit_analysis (project_id, analysis_date, delete_token),
    KEY idx_operation_profit_date (analysis_date, deleted),
    CONSTRAINT fk_operation_profit_project FOREIGN KEY (project_id) REFERENCES project_info(id),
    CONSTRAINT chk_operation_profit_amount CHECK (
        contract_amount >= 0 AND income_amount >= 0 AND cost_amount >= 0
        AND profit_amount = income_amount - cost_amount
    ),
    CONSTRAINT chk_operation_profit_rate CHECK (
        profit_rate IS NULL OR profit_rate BETWEEN -99999.9999 AND 100
    ),
    CONSTRAINT chk_operation_profit_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目利润分析表';

CREATE TABLE operation_target (
    id BIGINT NOT NULL COMMENT '经营目标ID',
    target_year INT NOT NULL COMMENT '目标年度，规范原year字段',
    target_type VARCHAR(50) NOT NULL COMMENT '目标类型',
    target_name VARCHAR(200) NOT NULL COMMENT '目标名称',
    target_value DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '目标值',
    actual_value DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际值',
    completion_rate DECIMAL(9,4) NULL COMMENT '完成率，百分比',
    responsible_org BIGINT NOT NULL COMMENT '责任组织ID',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_target (target_year, target_type, responsible_org, delete_token),
    KEY idx_operation_target_org (responsible_org, target_year, deleted),
    CONSTRAINT fk_operation_target_org FOREIGN KEY (responsible_org) REFERENCES sys_org(id),
    CONSTRAINT chk_operation_target_year CHECK (target_year BETWEEN 1900 AND 9999),
    CONSTRAINT chk_operation_target_value CHECK (target_value >= 0 AND actual_value >= 0),
    CONSTRAINT chk_operation_target_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度经营目标表';

CREATE TABLE operation_dashboard_snapshot (
    id BIGINT NOT NULL COMMENT '快照ID',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    income_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '收入总额',
    cost_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '成本总额',
    profit_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '利润总额',
    contract_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同总额',
    receivable_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '应收总额',
    project_count INT NOT NULL DEFAULT 0 COMMENT '项目数量',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_dashboard_date (snapshot_date, delete_token),
    CONSTRAINT chk_operation_dashboard_amount CHECK (
        income_total >= 0 AND cost_total >= 0 AND contract_total >= 0
        AND receivable_total >= 0 AND project_count >= 0
    ),
    CONSTRAINT chk_operation_dashboard_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营驾驶舱快照表';

-- project_info 在项目模块先创建，客户主数据在本脚本创建后补充跨域完整性约束。
ALTER TABLE project_info
    ADD KEY idx_project_info_customer (customer_id, status, deleted),
    ADD CONSTRAINT fk_project_info_customer FOREIGN KEY (customer_id)
        REFERENCES operation_customer(id);

SET FOREIGN_KEY_CHECKS = 1;
