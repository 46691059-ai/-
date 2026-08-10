# Database

MySQL 8 全量结构脚本位于 `mysql/` 根目录。Docker首次创建数据卷时只执行
`mysql/init/00_enterprise_platform.sql`，由该清单按固定顺序加载受控脚本。
`mysql/migration/` 保存已部署环境的升级脚本；达到当前Schema所必需且已经评审的版本，必须
由唯一初始化清单显式列入新库执行路径，禁止扫描目录自动执行。
`mysql/deprecated/` 只保存历史快照，不得用于初始化或升级。

- `01_database.sql`：创建并切换到 `enterprise_platform` 数据库。
- `02_sys.sql`：系统基础域，包含用户、角色、权限、菜单、组织、字典、日志、文件和消息。
- `03_hr.sql`：组织人事域，包含人员、岗位、干部、培训、绩效、薪酬和人才标签。
- `04_party.sql`：党建治理域，包含党组织、党员发展、组织生活、考核和党建经营融合。
- `05_project.sql`：项目全生命周期域，包含机会、阶段、WBS、经营分析、验收和归档。
- `06_operation.sql`：经营管理域，包含客户、供应商、合同、收入、成本、应收、回款、利润和经营分析。
- `07_investment.sql`：投资管理域，包含年度计划、投资事项、决策支付、股权治理、投后监管、收益和退出。
- `08_data_asset.sql`：数据资产域，包含数据来源、资源目录、治理质量、资产评估、产品、授权交易、收益和安全审计。
- `09_risk.sql`：风险合规域，包含风险分类、评估预警、整改闭环、内控、审计、巡察、廉洁和合同风险。
- `10_init_data.sql`：幂等初始化角色、组织、权限、菜单、字典、风险分类、安全等级和项目阶段模板；不创建默认登录账号。
- `deprecated/V1.0.0__enterprise_platform_v1.sql`：已停用的V1.0聚合兼容快照，仅供历史审计。
- `V1.1.0__investment_data_risk_bi.sql`：投资、数据资产、风险合规和 BI 分域。
- `migration/V2.0.0__legacy_to_v1.sql`：旧 `pm_project*` 数据迁移说明和保留表操作。
- `migration/V2.1.0`至`V2.1.3`：生命周期V2结构、阶段关联、LEGACY回填和标准模板。
- `mysql/manual/create_initial_admin.sql.example`：不会自动执行的首个管理员安全引导模板，必须注入唯一账号和BCrypt强摘要后手工运行。

生产升级前必须完成全量备份，并在预发布环境核对外键、记录数和金额汇总。
若部署时覆盖 `DB_NAME`，必须同步修改 `01_database.sql`，确保初始化库名与数据源一致。

Project域表结构以 `mysql/05_project.sql` 为唯一权威来源。禁止在聚合快照、风险域
兼容脚本或初始化数据脚本中再次维护Project表结构。
