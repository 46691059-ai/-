-- ============================================
-- 国企数字化治理与经营赋能平台
-- 系统首次部署初始化数据
-- 固定ID区间仅供系统种子数据使用，业务数据继续使用雪花ID
-- 本脚本可重复执行：所有写入均以业务唯一键判重
-- ============================================

USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

-- 1. 系统角色。非超级管理员角色仅初始化定义，具体授权由管理员按最小权限原则配置。
INSERT INTO sys_role (
    id, role_name, role_code, description, data_scope_type, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.role_name, s.role_code, s.description, s.data_scope_type, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 1 AS id, '系统管理员' AS role_name, 'SUPER_ADMIN' AS role_code,
           '拥有系统全部权限' AS description, 'ALL' AS data_scope_type
    UNION ALL SELECT 2, '董事长', 'CHAIRMAN', '查看企业经营、投资、风险全局数据', 'ALL'
    UNION ALL SELECT 3, '党委书记', 'PARTY_SECRETARY', '负责党建、三重一大和干部治理', 'ALL'
    UNION ALL SELECT 4, '总经理', 'GENERAL_MANAGER', '负责企业经营管理', 'ALL'
    UNION ALL SELECT 5, '部门负责人', 'DEPT_MANAGER', '负责本部门及下级组织业务', 'ORG_AND_CHILDREN'
    UNION ALL SELECT 6, '项目经理', 'PROJECT_MANAGER', '负责授权项目全过程管理', 'SELF'
    UNION ALL SELECT 7, '普通员工', 'COMMON_USER', '普通业务人员', 'SELF'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role t WHERE t.role_code = s.role_code AND t.deleted = 0
);

-- 2. 初始化组织。根组织不使用parent_id=0，避免伪外键。
INSERT INTO sys_org (
    id, org_code, org_name, org_type, parent_id, leader_id, tree_path, tree_level,
    sort_no, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 100, 'ROOT001', '集团总部', 'COMPANY', NULL, NULL, '/', 1,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_org WHERE org_code = 'ROOT001' AND deleted = 0
);

INSERT INTO sys_org (
    id, org_code, org_name, org_type, parent_id, leader_id, tree_path, tree_level,
    sort_no, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.org_code, s.org_name, 'DEPARTMENT', root_org.id, NULL,
       CONCAT('/', root_org.id, '/'), 2, s.sort_no, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 101 AS id, 'DEPT001' AS org_code, '综合管理部' AS org_name, 1 AS sort_no
    UNION ALL SELECT 102, 'DEPT002', '财务部', 2
    UNION ALL SELECT 103, 'DEPT003', '数字产业部', 3
    UNION ALL SELECT 104, 'DEPT004', '投资运营部', 4
) s
JOIN sys_org root_org ON root_org.org_code = 'ROOT001' AND root_org.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_org t WHERE t.org_code = s.org_code AND t.deleted = 0
);

-- 3. 功能权限。模块入口权限与已开发的项目REST权限分开管理。
INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, s.permission_type,
       s.resource_path, s.http_method, s.module_code, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 1001 AS id, '首页驾驶舱访问' AS permission_name, 'dashboard:view' AS permission_code,
           'BUTTON' AS permission_type, NULL AS resource_path, NULL AS http_method, 'dashboard' AS module_code
    UNION ALL SELECT 1002, '党建治理访问', 'party:view', 'BUTTON', NULL, NULL, 'party'
    UNION ALL SELECT 1003, '组织人事访问', 'hr:view', 'BUTTON', NULL, NULL, 'hr'
    UNION ALL SELECT 1004, '项目管理访问', 'project:view', 'BUTTON', NULL, NULL, 'project'
    UNION ALL SELECT 1005, '经营管理访问', 'operation:view', 'BUTTON', NULL, NULL, 'operation'
    UNION ALL SELECT 1006, '投资管理访问', 'investment:view', 'BUTTON', NULL, NULL, 'investment'
    UNION ALL SELECT 1007, '数据资产访问', 'data_asset:view', 'BUTTON', NULL, NULL, 'data_asset'
    UNION ALL SELECT 1008, '风险合规访问', 'risk:view', 'BUTTON', NULL, NULL, 'risk'
    UNION ALL SELECT 1009, '系统管理访问', 'system:view', 'BUTTON', NULL, NULL, 'system'
    UNION ALL SELECT 2001, '项目查询', 'project:lifecycle:list', 'API', '/projects/**', 'GET', 'project'
    UNION ALL SELECT 2002, '项目新增', 'project:lifecycle:create', 'API', '/projects', 'POST', 'project'
    UNION ALL SELECT 2003, '项目修改', 'project:lifecycle:update', 'API', '/projects/**', 'PUT', 'project'
    UNION ALL SELECT 2004, '项目删除', 'project:lifecycle:delete', 'API', '/projects/**', 'DELETE', 'project'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission t
    WHERE t.permission_code = s.permission_code AND t.deleted = 0
);

-- 4. 一级菜单与项目列表页。
INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, NULL, 'M', s.path, NULL, s.permission, s.icon, s.sort_no,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 100 AS id, '首页驾驶舱' AS menu_name, '/dashboard' AS path,
           'dashboard:view' AS permission, 'DataAnalysis' AS icon, 1 AS sort_no
    UNION ALL SELECT 200, '党建治理', '/party', 'party:view', 'Flag', 2
    UNION ALL SELECT 300, '组织人事', '/hr', 'hr:view', 'User', 3
    UNION ALL SELECT 400, '项目管理', '/projects', 'project:view', 'Management', 4
    UNION ALL SELECT 500, '经营管理', '/operation', 'operation:view', 'TrendCharts', 5
    UNION ALL SELECT 600, '投资管理', '/investment', 'investment:view', 'Money', 6
    UNION ALL SELECT 700, '数据资产', '/data-assets', 'data_asset:view', 'Coin', 7
    UNION ALL SELECT 800, '风险合规', '/risk', 'risk:view', 'Warning', 8
    UNION ALL SELECT 900, '系统管理', '/system', 'system:view', 'Setting', 9
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu t
    WHERE t.parent_id IS NULL AND t.permission = s.permission AND t.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 410, '项目库', parent_menu.id, 'C', '/projects',
       'project/ProjectListView', 'project:lifecycle:list', 'List', 1,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_menu parent_menu
WHERE parent_menu.permission = 'project:view' AND parent_menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu t
      WHERE t.permission = 'project:lifecycle:list' AND t.deleted = 0
  );

-- 5. 超级管理员角色拥有全部初始化权限和菜单，但不自动创建任何登录账号。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, role_row.id, permission_row.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 12001 AS id, 'dashboard:view' AS permission_code
    UNION ALL SELECT 12002, 'party:view'
    UNION ALL SELECT 12003, 'hr:view'
    UNION ALL SELECT 12004, 'project:view'
    UNION ALL SELECT 12005, 'operation:view'
    UNION ALL SELECT 12006, 'investment:view'
    UNION ALL SELECT 12007, 'data_asset:view'
    UNION ALL SELECT 12008, 'risk:view'
    UNION ALL SELECT 12009, 'system:view'
    UNION ALL SELECT 12010, 'project:lifecycle:list'
    UNION ALL SELECT 12011, 'project:lifecycle:create'
    UNION ALL SELECT 12012, 'project:lifecycle:update'
    UNION ALL SELECT 12013, 'project:lifecycle:delete'
) s
JOIN sys_role role_row
  ON role_row.role_code = 'SUPER_ADMIN' AND role_row.deleted = 0
JOIN sys_permission permission_row
  ON permission_row.permission_code = s.permission_code AND permission_row.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission t
    WHERE t.role_id = role_row.id AND t.permission_id = permission_row.id AND t.deleted = 0
);

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 13000 + s.sort_no, role_row.id, menu_row.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 1 AS sort_no, 'dashboard:view' AS permission
    UNION ALL SELECT 2, 'party:view'
    UNION ALL SELECT 3, 'hr:view'
    UNION ALL SELECT 4, 'project:view'
    UNION ALL SELECT 5, 'project:lifecycle:list'
    UNION ALL SELECT 6, 'operation:view'
    UNION ALL SELECT 7, 'investment:view'
    UNION ALL SELECT 8, 'data_asset:view'
    UNION ALL SELECT 9, 'risk:view'
    UNION ALL SELECT 10, 'system:view'
) s
JOIN sys_role role_row
  ON role_row.role_code = 'SUPER_ADMIN' AND role_row.deleted = 0
JOIN sys_menu menu_row
  ON menu_row.permission = s.permission AND menu_row.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu t
    WHERE t.role_id = role_row.id AND t.menu_id = menu_row.id AND t.deleted = 0
);

-- 6. 业务字典。字典值与后端枚举、数据库注释保持一致。
INSERT INTO sys_dict (
    id, dict_type, dict_label, dict_value, sort_no, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.dict_type, s.dict_label, s.dict_value, s.sort_no, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 20001 AS id, 'project_type' AS dict_type, '投资项目' AS dict_label, '01' AS dict_value, 1 AS sort_no
    UNION ALL SELECT 20002, 'project_type', '中标项目', '02', 2
    UNION ALL SELECT 20003, 'project_type', '工程项目', '03', 3
    UNION ALL SELECT 20004, 'project_type', '数字化项目', '04', 4
    UNION ALL SELECT 20005, 'project_type', '研发项目', '05', 5
    UNION ALL SELECT 20006, 'project_type', '运营项目', '06', 6
    UNION ALL SELECT 20101, 'project_status', '储备中', 'RESERVED', 1
    UNION ALL SELECT 20102, 'project_status', '进行中', 'IN_PROGRESS', 2
    UNION ALL SELECT 20103, 'project_status', '已暂停', 'SUSPENDED', 3
    UNION ALL SELECT 20104, 'project_status', '已完成', 'COMPLETED', 4
    UNION ALL SELECT 20105, 'project_status', '已取消', 'CANCELLED', 5
    UNION ALL SELECT 20201, 'project_stage', '项目储备', 'RESERVE', 1
    UNION ALL SELECT 20202, 'project_stage', '项目论证', 'DEMONSTRATION', 2
    UNION ALL SELECT 20203, 'project_stage', '项目立项', 'INITIATION', 3
    UNION ALL SELECT 20204, 'project_stage', '项目实施', 'IMPLEMENTATION', 4
    UNION ALL SELECT 20205, 'project_stage', '项目验收', 'ACCEPTANCE', 5
    UNION ALL SELECT 20206, 'project_stage', '项目运营', 'OPERATION', 6
    UNION ALL SELECT 20207, 'project_stage', '项目评价', 'EVALUATION', 7
    UNION ALL SELECT 20208, 'project_stage', '项目归档', 'ARCHIVE', 8
    UNION ALL SELECT 20301, 'investment_type', '股权投资', 'EQUITY', 1
    UNION ALL SELECT 20302, 'investment_type', '固定资产投资', 'FIXED_ASSET', 2
    UNION ALL SELECT 20303, 'investment_type', '基金投资', 'FUND', 3
    UNION ALL SELECT 20304, 'investment_type', '数据资产投资', 'DATA_ASSET', 4
    UNION ALL SELECT 20305, 'investment_type', '其他投资', 'OTHER', 5
    UNION ALL SELECT 20401, 'contract_type', '销售合同', 'SALES', 1
    UNION ALL SELECT 20402, 'contract_type', '服务合同', 'SERVICE', 2
    UNION ALL SELECT 20403, 'contract_type', '采购合同', 'PURCHASE', 3
    UNION ALL SELECT 20404, 'contract_type', '合作协议', 'COOPERATION', 4
    UNION ALL SELECT 20405, 'contract_type', '投资协议', 'INVESTMENT', 5
    UNION ALL SELECT 20501, 'risk_level', '低风险', 'LOW', 1
    UNION ALL SELECT 20502, 'risk_level', '一般风险', 'MEDIUM', 2
    UNION ALL SELECT 20503, 'risk_level', '较大风险', 'HIGH', 3
    UNION ALL SELECT 20504, 'risk_level', '重大风险', 'CRITICAL', 4
    UNION ALL SELECT 20601, 'party_activity_type', '支委会', '01', 1
    UNION ALL SELECT 20602, 'party_activity_type', '党员大会', '02', 2
    UNION ALL SELECT 20603, 'party_activity_type', '党小组会', '03', 3
    UNION ALL SELECT 20604, 'party_activity_type', '党课', '04', 4
    UNION ALL SELECT 20605, 'party_activity_type', '主题党日', '05', 5
    UNION ALL SELECT 20701, 'meeting_type', '党委会', 'PARTY_COMMITTEE', 1
    UNION ALL SELECT 20702, 'meeting_type', '经理办公会', 'MANAGER_MEETING', 2
    UNION ALL SELECT 20703, 'meeting_type', '董事会', 'BOARD', 3
    UNION ALL SELECT 20704, 'meeting_type', '股东会', 'SHAREHOLDER', 4
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict t
    WHERE t.dict_type = s.dict_type AND t.dict_value = s.dict_value AND t.deleted = 0
);

-- 7. 风险分类基础数据。
INSERT INTO risk_category (
    id, category_code, category_name, parent_id, description, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.category_code, s.category_name, NULL, NULL, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 30001 AS id, '01' AS category_code, '战略风险' AS category_name
    UNION ALL SELECT 30002, '02', '投资风险'
    UNION ALL SELECT 30003, '03', '经营风险'
    UNION ALL SELECT 30004, '04', '财务风险'
    UNION ALL SELECT 30005, '05', '法律风险'
    UNION ALL SELECT 30006, '06', '安全风险'
    UNION ALL SELECT 30007, '07', '廉洁风险'
    UNION ALL SELECT 30008, '08', '数据安全风险'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM risk_category t
    WHERE t.category_code = s.category_code AND t.deleted = 0
);

-- 8. 数据安全等级。等级编码为稳定主数据，不允许删除后复用。
INSERT INTO data_security_level (
    id, level_code, level_name, description, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.level_code, s.level_name, s.description, 'ACTIVE',
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 31001 AS id, 'L1' AS level_code, '一般数据' AS level_name, '公开或低敏感数据' AS description
    UNION ALL SELECT 31002, 'L2', '内部数据', '仅限企业内部授权使用'
    UNION ALL SELECT 31003, 'L3', '敏感数据', '涉及经营信息或个人信息'
    UNION ALL SELECT 31004, 'L4', '核心数据', '涉及企业重要战略或核心利益'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM data_security_level t
    WHERE t.level_code = s.level_code AND t.deleted = 0
);

-- 9. 通用项目阶段模板。具体项目类型可在此基础上配置覆盖模板。
INSERT INTO project_stage_template (
    id, project_type, stage_code, stage_name, stage_order, requires_approval, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, 'ALL', s.stage_code, s.stage_name, s.stage_order, s.requires_approval, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 40001 AS id, 'RESERVE' AS stage_code, '项目储备' AS stage_name, 1 AS stage_order, 0 AS requires_approval
    UNION ALL SELECT 40002, 'DEMONSTRATION', '项目论证', 2, 0
    UNION ALL SELECT 40003, 'INITIATION', '项目立项', 3, 1
    UNION ALL SELECT 40004, 'IMPLEMENTATION', '项目实施', 4, 0
    UNION ALL SELECT 40005, 'ACCEPTANCE', '项目验收', 5, 1
    UNION ALL SELECT 40006, 'OPERATION', '项目运营', 6, 0
    UNION ALL SELECT 40007, 'EVALUATION', '项目评价', 7, 0
    UNION ALL SELECT 40008, 'ARCHIVE', '项目归档', 8, 0
) s
WHERE NOT EXISTS (
    SELECT 1 FROM project_stage_template t
    WHERE t.project_type = 'ALL' AND t.stage_code = s.stage_code AND t.deleted = 0
);

COMMIT;

-- 安全说明：本脚本故意不创建sys_user或sys_user_role记录。
-- 首个管理员必须由受信任的部署流程在运行时读取密钥管理系统中的一次性口令，
-- 使用BCrypt cost >= 12生成摘要，并在同一事务中创建用户及绑定SUPER_ADMIN角色。
-- 禁止把明文口令、固定摘要、手机号或可直接登录的默认账号提交到代码仓库。
