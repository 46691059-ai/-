-- Docker MySQL唯一初始化入口。
-- 只允许在本文件中维护新库初始化执行顺序；仅显式列出的正式migration可进入新库链路。
-- manual、deprecated及未评审migration禁止加入。

SOURCE /opt/enterprise/sql/01_database.sql
SOURCE /opt/enterprise/sql/02_sys.sql
SOURCE /opt/enterprise/sql/03_hr.sql
SOURCE /opt/enterprise/sql/04_party.sql
SOURCE /opt/enterprise/sql/05_project.sql
SOURCE /opt/enterprise/sql/06_operation.sql
SOURCE /opt/enterprise/sql/07_investment.sql
SOURCE /opt/enterprise/sql/08_data_asset.sql
SOURCE /opt/enterprise/sql/09_risk.sql
SOURCE /opt/enterprise/sql/10_init_data.sql
SOURCE /opt/enterprise/sql/11_sprint_1_user_permissions.sql
SOURCE /opt/enterprise/sql/12_sprint_1_org_permissions.sql
SOURCE /opt/enterprise/sql/13_sprint_1_role_permissions.sql
SOURCE /opt/enterprise/sql/14_sprint_1_rbac_acceptance.sql
SOURCE /opt/enterprise/sql/15_sprint_1_menu_center.sql
SOURCE /opt/enterprise/sql/16_sprint_1_log_center.sql

-- 第二批投资、数据资产、风险和BI兼容结构暂时显式保留。
-- 该文件不定义Project核心表，不得重新引入deprecated聚合V1.0。
SOURCE /opt/enterprise/sql/V1.1.0__investment_data_risk_bi.sql

-- Lifecycle V2：新库同样必须达到当前正式Schema版本。
SOURCE /opt/enterprise/sql/migration/V2.1.0__create_project_lifecycle_v2_structure.sql
SOURCE /opt/enterprise/sql/migration/V2.1.1__link_project_stage_lifecycle_snapshot.sql
SOURCE /opt/enterprise/sql/migration/V2.1.2__backfill_legacy_project_lifecycle.sql
SOURCE /opt/enterprise/sql/migration/V2.1.3__seed_project_lifecycle_templates.sql
