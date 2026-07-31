# Database

MySQL 8 全量初始化脚本位于 `mysql/` 根目录，Docker 首次创建数据卷时自动执行。
`mysql/migration/` 仅存放已部署旧版环境的人工升级脚本，不会被容器初始化程序执行。

- `01_database.sql`：创建并切换到 `enterprise_platform` 数据库。
- `02_sys.sql`：系统基础域，包含用户、角色、权限、菜单、组织、字典、日志、文件和消息。
- `03_hr.sql`：组织人事域，包含人员、岗位、干部、培训、绩效、薪酬和人才标签。
- `04_party.sql`：党建治理域，包含党组织、党员发展、组织生活、考核和党建经营融合。
- `05_project.sql`：项目全生命周期域，包含机会、阶段、WBS、经营分析、验收和归档。
- `V1.0.0__enterprise_platform_v1.sql`：V1.0 完整基线及最小权限菜单数据。
- `V1.1.0__investment_data_risk_bi.sql`：投资、数据资产、风险合规和 BI 分域。
- `migration/V2.0.0__legacy_to_v1.sql`：旧 `pm_project*` 数据迁移说明和保留表操作。

生产升级前必须完成全量备份，并在预发布环境核对外键、记录数和金额汇总。
若部署时覆盖 `DB_NAME`，必须同步修改 `01_database.sql`，确保初始化库名与数据源一致。
