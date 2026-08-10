# Flyway Migration基础设施接入说明

> 版本：V1.0
> 对应阶段：Sprint 2-1.8
> 状态：基础设施已接入，未执行Migration
> 生产原则：Migration由独立作业执行，不随Spring Boot应用启动

## 1. 接入结论

本阶段采用独立Flyway CLI/Container接入，不在Spring Boot POM中加入Flyway依赖，也不修改 `application.yml`：

```text
开发/测试/预生产/生产
  → 同一Flyway容器版本
  → 只挂载对应厂商的权威Migration目录
  → 迁移前策略检查（版本、历史状态、资产SHA-256、info、起始指纹）
  → 经显式确认后由独立作业migrate
  → 迁移后严格validate + 目标版本与Schema指纹
```

原因：

1. 生产多副本应用启动不应与DDL执行耦合；
2. Migration需要独立高权限账号、审批、备份、维护窗口和审计；
3. 当前仓库已有SQL-first资产，CLI/Container无需侵入业务运行时；
4. 后续可在本地开发配置可选Spring Boot集成，但它不能成为生产唯一执行路径；
5. 本阶段目标是治理能力接入，不是执行V2.4业务Migration。

### 1.1 Spring Boot依赖评估

| 方案 | 本阶段结论 | 说明 |
|---|---|---|
| `flyway-core`随应用启动 | 不接入 | 生产风险和权限耦合，不符合治理设计 |
| Spring Boot仅做validate | 暂不接入 | 会增加运行时依赖；当前独立作业已覆盖 |
| Flyway Maven插件 | 暂不接入 | 仍需数据库凭据和执行环境，CLI更统一 |
| Flyway CLI/Container | **采用** | 跨环境一致、可固定镜像、独立账号、易归档输出 |

若未来引入Spring Boot依赖，生产必须保持 `spring.flyway.enabled=false`，并由专项变更同步验证Flyway与Spring Boot依赖版本，不在业务功能提交中顺带加入。

## 2. 目录结构与已接入文件

```text
database
├─ migration
│  ├─ README.md
│  ├─ mysql/README.md
│  ├─ dm/README.md
│  └─ kingbase/README.md
├─ flyway
│  ├─ .env.example
│  ├─ migration-inventory.yml
│  └─ scripts
│     ├─ flyway.ps1
│     ├─ flyway.sh
│     ├─ check-environment.ps1
│     ├─ check-environment.sh
│     ├─ release.ps1
│     └─ release.sh
└─ mysql
   └─ verification
      └─ schema_fingerprint.sql
```

### 2.1 权威扫描目录

| 数据库 | 自动扫描目录 | 当前可执行Migration |
|---|---|---|
| MySQL | `database/migration/mysql` | V2.1.0—V2.1.3 |
| 达梦 | `database/migration/dm` | 无 |
| 人大金仓 | `database/migration/kingbase` | 无 |

MySQL目录已在Sprint 2-1.10晋级V2.1.0—V2.1.3，并以 `SHA256SUMS` 冻结资产摘要。未来V2.4脚本仍须经评审后放入对应厂商目录。

### 2.2 未纳入自动扫描的目录

以下资产永远不应作为新执行器的默认扫描路径：

- `database/mysql/manual`：验收Fixture与管理员示例；
- `database/mysql/deprecated`：废弃历史聚合SQL；
- `database/mysql/init`：MySQL空卷初始化入口；
- `database/mysql/01_...16_*.sql`：历史初始化/模块脚本；
- `database/mysql/migration`：历史来源留档；V2.1.0—V2.1.3的权威执行副本已晋级，旧目录不再作为执行位置；
- 任意 `rollback`、`verification` 和文档目录。

当前 `deploy/docker-compose.yml` 仍把 `database/mysql/init` 挂载到MySQL初始化目录。该机制只在空数据卷首次启动时运行，不是版本管理器；在B2.1.3 Baseline正式生成并验证前保留现状，本Sprint不修改它。

## 3. Flyway安全包装器

### 3.1 支持命令

仓库包装器只允许：

```text
info
validate
migrate
baseline
```

`clean` 和 `repair` 未加入参数白名单，调用会被拒绝。包装器不会自动repair，也不会在validate失败后继续migrate。

### 3.2 固定安全参数

包装器统一设置：

```text
locations=filesystem:/flyway/sql
table=flyway_schema_history
validateMigrationNaming=true
validateOnMigrate=true
baselineOnMigrate=false
cleanDisabled=true
outOfOrder=false
mixed=false
connectRetries=3
```

Migration目录以只读方式挂载进容器。凭据通过宿主环境变量传入，不作为带值命令行参数输出。

### 3.3 变更命令保护

`migrate` 和 `baseline` 必须显式设置：

```text
CONFIRM_FLYWAY_MUTATION=<environment>:<command>
```

例如测试环境migrate需要 `test:migrate`。预生产和生产还必须设置非空 `CHANGE_TICKET`。这只是误操作保护，不替代发布审批、备份和DBA授权。

### 3.4 Flyway镜像

示例配置使用精确标签 `redgate/flyway:13.0.0`，不使用`latest`。生产实施前还必须：

- 完成许可证和安全评审；
- 记录不可变镜像摘要；
- 固定JDBC驱动版本；
- 在MySQL、达梦、人大金仓目标版本分别认证；
- 工具升级与业务Migration分开发布。

示例标签仅用于接入模板，本阶段未拉取或运行该镜像。

## 4. 环境变量

模板位于 `database/flyway/.env.example`。真实配置必须由安全环境文件或CI/CD Secrets提供。

| 变量 | 必需 | 说明 |
|---|:---:|---|
| `FLYWAY_IMAGE` | 是 | 精确Flyway镜像标签/摘要 |
| `FLYWAY_VENDOR` | 是 | mysql/dm/kingbase |
| `FLYWAY_URL` | 是 | JDBC URL |
| `FLYWAY_USER` | 是 | Migration专用账号 |
| `FLYWAY_PASSWORD` | 是 | Migration密码，不得提交 |
| `FLYWAY_DOCKER_NETWORK` | 否 | 目标数据库所在Docker网络 |
| `DB_NAME` | 是 | 指纹与历史查询的Schema |
| `EXPECTED_DB_VERSION` | 预生产/生产必需 | 期望当前版本 |
| `EXPECTED_SCHEMA_FINGERPRINT` | 预生产/生产必需 | 期望物理Schema指纹 |
| `MYSQL_CLIENT_DEFAULTS_FILE` | MySQL检查必需 | mysql客户端安全配置文件 |
| `CHANGE_TICKET` | 预生产/生产变更必需 | 审批单号 |
| `CONFIRM_FLYWAY_MUTATION` | 变更命令必需 | 显式确认值 |

### 4.1 MySQL客户端安全配置示例

环境检查脚本不接受明文密码参数，要求配置一个权限为0600的文件：

```ini
[client]
host=127.0.0.1
port=3306
user=enterprise_readonly
password=
default-character-set=utf8mb4
```

该文件由流水线在运行时把密码写入空值位置，随后设置0600权限；不得把完成注入的文件提交到Git。指纹检查账号只需要 `information_schema` 元数据和 `flyway_schema_history` 读取权限。

## 5. 分阶段验证流程

### 5.1 迁移前策略检查与迁移后严格验证

```mermaid
flowchart LR
    A["Pre: 检查历史表与失败记录"] --> B["比较起始版本/起始指纹"]
    B --> C["校验SHA256SUMS与SQL全集"]
    C --> D["Flyway info，允许pending"]
    D --> E["显式审批后migrate"]
    E --> F["Post: 严格Flyway validate"]
    F --> G["比较目标版本/目标指纹"]
    G --> H["通过/失败关闭"]
```

`check-environment` 脚本本身不会执行migrate、baseline、repair或clean；`release` 脚本按 `pre -> migrate -> post` 固定编排。迁移前不运行严格validate，避免把合法pending版本误判为发布失败；`migrate` 仍保持 `validateOnMigrate=true`，迁移后再执行不忽略任何规则的严格validate。

### 5.2 PowerShell

```powershell
# 先从安全来源设置环境变量，不要把密码写进命令历史。
& .\database\flyway\scripts\check-environment.ps1 test pre
& .\database\flyway\scripts\release.ps1 test
```

### 5.3 Linux/CI

```bash
# secure-flyway.env 必须是未跟踪且权限受限的文件。
set -a
source /run/secrets/secure-flyway.env
set +a

bash database/flyway/scripts/check-environment.sh preprod pre
bash database/flyway/scripts/release.sh preprod
```

### 5.4 Validate失败处理

遇到以下情况立即失败：

- 历史表不存在；
- 目标版本不一致；
- 已执行脚本缺失、名称或checksum变化；
- 存在失败、未来或无法解析的Migration；
- Schema指纹不一致；
- 生产/预生产缺少期望版本或期望指纹；
- 厂商指纹适配器尚未实现。

失败后：保存输出 → 冻结发布 → 只读审计 → 编写更高版本前向修复或执行已批准纳管流程。禁止自动repair或手工修改 `flyway_schema_history`。

## 6. Schema指纹实现

### 6.1 当前能力

`database/mysql/verification/schema_fingerprint.sql` 从MySQL `information_schema` 稳定输出：

- 表、类型、引擎、字符集/排序规则和注释；
- 字段顺序、类型、可空、默认值、extra、字符集和注释；
- 主键、唯一键、普通索引及字段顺序；
- 外键和更新/删除规则；
- CHECK约束；
- 视图定义。

输出按稳定键排序，排除 `flyway_schema_history` 和未来发布审计表，然后由脚本计算UTF-8 SHA-256。

### 6.2 边界

- 当前只实现MySQL 8物理指纹；
- 达梦和人大金仓检查脚本明确失败，不会假装兼容；
- 两个PowerShell/Linux环境应使用同一数据库和字符编码进行交叉验证；生产权威指纹建议由Linux CI生成；
- 指纹变化只负责发现漂移，不自动修复；
- 跨厂商逻辑指纹仍需后续实现类型规范化映射。

## 7. 环境规范

### 7.1 开发

- 允许 `info/validate`；
- `migrate`只针对个人一次性数据库，需显式确认；
- 历史重建通过销毁本地数据容器，不使用Flyway clean；
- 可暂不设置期望指纹，但必须输出并记录实际值；
- 禁止连接共享测试或生产地址。

### 7.2 测试

- 由CI账号执行，开发者不手工补表；
- 同时验证空库/基线和上一版本存量升级路径；
- 必须运行info、validate、指纹、Migration测试和业务集成测试；
- 记录执行日志、制品摘要和数据库版本。

### 7.3 预生产

- 必须设置 `EXPECTED_DB_VERSION` 和 `EXPECTED_SCHEMA_FINGERPRINT`；
- 使用生产脱敏快照完整演练；
- 执行备份恢复、锁、耗时、容量和应用回退测试；
- migrate需要变更单与显式确认；
- 通过后输出生产发布Runbook，不允许直接复用临时命令。

### 7.4 生产

- 应用启动不执行Migration；
- 独立单实例作业使用Migration账号，应用账号没有DDL权限；
- 发布前先执行环境检查，失败即停止；
- 必须具备备份/PITR和已演练恢复方案；
- 迁移制品、Flyway镜像和JDBC驱动均固定SHA-256/摘要；
- 执行后重新计算版本和指纹，再灰度发布应用；
- 禁止自动repair、clean、outOfOrder和baselineOnMigrate；
- 观察期内不清理旧表、旧列和兼容读路径。

## 8. Migration Inventory

机器可读清单位于 `database/flyway/migration-inventory.yml`。

### 8.1 已执行

当前无法从仓库确认任何Migration已在真实数据库执行：

- 仓库中没有目标数据库的 `flyway_schema_history` 导出；
- 本Sprint未连接真实数据库；
- 文件存在不等于已执行。

因此清单中的 `confirmed_applied` 为空。首次纳管时必须由环境检查读取数据库历史，并结合Schema指纹确认，禁止人为把“已有SQL文件”标记为已执行。

### 8.2 已实现但执行状态未知

| 版本 | 资产 | 分类 | 自动扫描 |
|---|---|---|:---:|
| V2.0.0 | `legacy_to_v1` | 特定pm_*旧库升级 | 否 |
| V2.1.0 | Lifecycle V2结构 | 已实现资产，执行未知 | 否 |
| V2.1.1 | project_stage关联快照 | 已实现资产，执行未知 | 否 |
| V2.1.2 | LEGACY回填 | 已实现资产，执行未知 | 否 |
| V2.1.3 | 生命周期模板种子 | 已实现资产，执行未知 | 否 |

V2.0.0只适用于特定旧库，绝不能在新库或标准V1/V2库自动执行。V2.1.x应通过目标库结构和数据核验决定“已存在后baseline纳管”还是“专项升级”，不能直接复制到新扫描目录重放。

### 8.3 规划

| 范围 | 状态 | 文件 |
|---|---|---|
| V2.4.0—V2.4.x Investment链 | PLANNED | 尚未创建 |
| B2.1.3 Foundation Baseline | PLANNED | 尚未创建 |
| 达梦/人大金仓等价链 | PLANNED | 尚未创建 |

规划资产不进入Flyway扫描路径，不在历史表伪造记录。

### 8.4 废弃与非Migration

| 资产 | 分类 | 自动扫描 |
|---|---|:---:|
| `deprecated/V1.0.0__enterprise_platform_v1.sql` | 废弃聚合版本 | 否 |
| `01_database.sql`—`16_sprint_1_log_center.sql` | 历史初始化/模块脚本 | 否 |
| `V1.1.0__investment_data_risk_bi.sql` | 扫描根外的未纳管初始化资产 | 否 |
| `init/00_enterprise_platform.sql` | Docker空卷初始化入口 | 否 |
| `manual/*` | 验收Fixture/人工示例 | 否 |

## 9. 执行与纳管流程

### 9.1 标准既有库

```text
备份与恢复演练
  → 使用旧基线识别SQL确认结构
  → 生成Schema指纹和关键数据报告
  → 确认V2.1.3对象/数据是否真实存在
  → DBA与系统负责人审批
  → 使用独立作业baseline到确认版本
  → 迁移前策略检查
  → migrate
  → 迁移后validate + 目标指纹
  → 才允许V2.4+
```

包装器虽然提供baseline命令，但必须有 `CONFIRM_FLYWAY_MUTATION`；预生产/生产还要求 `CHANGE_TICKET`。脚本不会自动判断某个库应baseline到哪个版本。

### 9.2 新空库

当前仍由既有init资产构建，不应直接运行空的权威Migration目录。正式切换前必须：

1. 生成B2.1.3 Baseline；
2. 与完整历史初始化结果比较Schema和参考数据指纹；
3. 完成空库、存量库和恢复测试；
4. 修改Docker初始化入口需单独Sprint和确认；
5. 新旧入口不得同时创建同一业务表。

### 9.3 漂移/混合库

环境检查失败后不得baseline、repair或migrate。先输出实际结构、历史表和差异，再通过更高版本对齐Migration或经审批恢复未授权漂移。

## 10. CI/CD建议

### 10.1 PR检查

- 新文件必须位于正确厂商扫描目录；
- 命名满足 `Vx.y.z__lower_snake_case.sql`；
- 版本不重复、不乱序；
- 已存在Migration哈希与Inventory/发布记录一致；
- SQL危险语句、明文敏感信息、无WHERE更新被阻断；
- 脚本头、回滚边界、前后检查和国产库状态齐全；
- 一次性MySQL执行完整链并校验指纹。

### 10.2 发布作业

```text
pre-policy-check
  → artifact/SHA-256 verification
  → source version/fingerprint
  → backup gate
  → manual approval
  → flyway migrate
  → strict flyway validate
  → target version/schema fingerprint
  → business verification SQL
  → application canary
```

生产作业不得暴露repair/clean按钮。应急repair使用独立DBA Runbook和双人审批。

## 11. 风险清单

| 风险 | 当前状态 | 控制措施 |
|---|---|---|
| 真实数据库执行历史未知 | 阻断纳管 | Inventory标记UNKNOWN，连接后以历史+指纹确认 |
| V2.0.0仅适用特定旧库 | 高风险 | 不进入新扫描路径 |
| V2.1.x为未跟踪/未确认资产 | 待治理 | 保持原位，不复制、不重放 |
| B2.1.3尚未生成 | 阻断新空库Flyway化 | 专项生成和等价指纹测试 |
| Flyway镜像未做安全/许可审批 | 待确认 | 固定版本和摘要后再用于共享环境 |
| MySQL客户端是指纹脚本依赖 | 已知 | CI/迁移镜像预装受控客户端 |
| 达梦/人大金仓指纹未实现 | 明确阻断 | 对非MySQL环境失败关闭 |
| PowerShell/Linux编码差异 | 待验证 | 生产指纹以Linux CI为权威，做交叉测试 |
| Docker init与Flyway并存 | 过渡风险 | Flyway目录无业务脚本；Baseline确认后专项切换 |
| 凭据进入环境变量 | 受控风险 | CI Secrets、短期账号、日志脱敏；mysql使用defaults文件 |
| Flyway history存在但Schema漂移 | 已覆盖 | checksum + Schema指纹双重门禁 |
| 自动repair掩盖问题 | 已阻断 | 包装器不支持repair，失败后人工审计 |

## 12. 后续任务

1. 确认Flyway精确版本、镜像摘要、许可证和JDBC驱动；
2. 连接开发验收库，执行只读Inventory、版本和Schema指纹核查；
3. 生成并验证B2.1.3 Baseline；
4. 决定V2.1.x目标库纳管策略并冻结其哈希；
5. 将环境检查加入CI；
6. 建立Migration专用账号和发布审计；
7. 创建V2.4.0前置检查脚本并进行空库/存量库测试；
8. 为达梦和人大金仓实现独立Flyway及指纹适配器。

在上述第1—4项完成前，不执行baseline或V2.4正式Migration，不将Flyway加入生产应用启动流程。
