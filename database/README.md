# Database

MySQL 8 全量初始化脚本位于 `mysql/` 根目录，Docker 首次创建数据卷时自动执行。
`mysql/migration/` 仅存放已部署旧版环境的人工升级脚本，不会被容器初始化程序执行。

- `V1.0.0__enterprise_platform_v1.sql`：V1.0 完整基线及最小权限菜单数据。
- `V1.1.0__investment_data_risk_bi.sql`：投资、数据资产、风险合规和 BI 分域。
- `migration/V2.0.0__legacy_to_v1.sql`：旧 `pm_project*` 数据迁移说明和保留表操作。

生产升级前必须完成全量备份，并在预发布环境核对外键、记录数和金额汇总。
