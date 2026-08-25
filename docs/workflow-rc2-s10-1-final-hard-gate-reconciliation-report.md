# Workflow V1 RC2-S10.1 Final Hard Gate Reconciliation

## 1. 结论

本轮仅关闭 RC2-S10 未完成的 Mapping Checker、Secret/PII Scan 与 Fixture
Discovery。三项均通过，允许进入一次短版 Final RC2-S10 Gate Re-run；不授权
Canary、ROLE Runtime、Release Commit 或 Scope 变更。

## 2. Terminal Token审计

`workflow_role_runtime_execution_admission_event` 的
`decision_terminal_token` 与 `closure_terminal_token` 均为 MySQL 8.4
`STORED GENERATED`：前者在 `APPROVED_FOR_EXECUTION/BLOCKED/REJECTED` 时生成
`admission_row_id`，后者在 `REVOKED/EXPIRED` 时生成 `admission_row_id`。两个字段
均有独立 UNIQUE 索引。

Java Entity、Domain、Entity Mapper、MyBatis Mapper 与 Repository 均不读、不写这两个
值；Runtime 依赖的是数据库拒绝重复终态，而不是 Java 字段值。因此分类为
`LEGITIMATE_DATABASE_SUPERSET_GENERATED_COLUMNS`，不得向 Entity 添加无意义字段。

V2.6.15 真实 MySQL 终态证据复用
`workflow-role-runtime-execution-admission-v2615-final-revalidation-report.md`：合法
APPROVED 与 REVOKED 成功，APPROVED 后 REJECTED、REVOKED 后 EXPIRED 被状态链或
UNIQUE 拒绝。S10.1 隔离 MySQL 8.4.9 再次读取 `EXTRA`、
`GENERATION_EXPRESSION` 与索引定义，结果一致。

## 3. DatabaseMappingChecker治理

Checker 现在读取 JDBC `IS_GENERATEDCOLUMN` 元数据，仅当字段同时满足
`databaseOnly && generated && entity未声明` 时归入
`ALLOWED_GENERATED_DATABASE_SUPERSET`。普通 DB-only 字段继续归入
`INCONSISTENT`，未忽略整表或全部 DB superset。

真实 V2.6.23 Schema 回归：75 Entity，70 matched，5 个既有 Investment 普通列差异，
0 个 Workflow 真漂移，2 个允许的 Workflow generated superset，0 missing table。
专项测试 5/5 PASS。

## 4. Secret Scan完整命中

扫描规则覆盖 private key、literal credential、URI embedded credential、AWS/GitHub/OpenAI
token marker。候选共9，真实Secret为0：

| path:line | rule | 分类 | 说明 |
|---|---|---|---|
| `deploy/scripts/backup.sh:37` | credential assignment | FALSE_POSITIVE | 从 `/run/secrets/mysql_app_password` 读取运行时变量 |
| `deploy/scripts/backup.sh:41` | credential argument | FALSE_POSITIVE | 使用上述 shell 变量，无字面Secret |
| `deploy/scripts/backup.sh:57` | credential assignment | FALSE_POSITIVE | 从 `/run/secrets/redis_password` 读取运行时变量 |
| `deploy/scripts/restore-mysql.sh:27` | credential assignment | FALSE_POSITIVE | 从 secret file 读取运行时变量 |
| `deploy/scripts/restore-mysql.sh:31` | credential argument | FALSE_POSITIVE | 使用上述 shell 变量 |
| `frontend/src/api/user.test.ts:28` | password literal | TEST_PLACEHOLDER | 测试请求固定值 |
| `frontend/src/api/user.test.ts:30` | password literal | TEST_PLACEHOLDER | 测试断言固定值 |
| `frontend/src/store/user.test.ts:43` | password literal | TEST_PLACEHOLDER | 测试登录固定值 |
| `frontend/src/store/user.test.ts:58` | password literal | TEST_PLACEHOLDER | 测试登录固定值 |

PEM private key、嵌入式URL凭据、云访问键及高置信生产Token命中均为0。Allowlist只按
上述精确路径、规则和固定模式分类，没有忽略整个 docs、test 或 deploy 目录。

## 5. PII Scan完整分类

候选共153，真实PII为0。128项为显式 Synthetic Test 标识：
`990201/990202`、`RC2_TEST_*`、`RC1_TEST_CANARY_APPROVER`。它们只表达冻结测试
业务键或 Canonical Vector；逐文件计数为：Provider Facade 1、Provider Contract Test 1、
Fixture Contract Test 3、Fixture Seeder 34、EffectiveAt Contract 15、V2.6.23 Mapping Test 6、
fixture identity SQL 13、publish script 8、verify SQL 11、collector 11、manifest 5、marker 1、
attestation verifier 1、fixture verifier 3、历史 RC2 fixture/validation 文档 15。

其余25项逐项如下：

- `V2617ApprovalRoleDirectoryMigrationContractTest.java:11`：手机号形状数字位于 SHA-256，`FALSE_POSITIVE`。
- `SensitiveDataSanitizerTest.java:17,20,27,29`：身份证测试值，4项，`SYNTHETIC_TEST_IDENTIFIER`。
- `SensitiveDataSanitizerTest.java:18,20,27,29`：手机号测试值，4项，`SYNTHETIC_TEST_IDENTIFIER`。
- `ProfileServiceImplTest.java:39`：测试手机号，`SYNTHETIC_TEST_IDENTIFIER`。
- `JdkHttpRoleDirectoryClientIntegrationTest.java:25`：用于验证敏感字段不进入结果的测试手机号，`SYNTHETIC_TEST_IDENTIFIER`。
- `migration-inventory.yml:508,933,1004`：SHA/fingerprint 数字段，3项，`FALSE_POSITIVE`。
- `database/migration/mysql/README.md:731`、`SHA256SUMS:38`：V2.6.16 SHA 数字段，2项，`FALSE_POSITIVE`。
- `approval-role-directory-provider-persistent-audit-p0-closure-report.md:86`、
  `workflow-multi-resolver-binding-v265-validation-report.md:130`：Schema fingerprint 数字段，2项，`FALSE_POSITIVE`。
- `workflow-role-runtime-claim-runtime-integration-report.md:7`、
  `workflow-role-runtime-external-capability-directory-integration-report.md:20`、
  `workflow-role-runtime-realtime-eligibility-evidence-v2616-validation-report.md:26`、
  `workflow-role-runtime-wf525-final-external-capability-canary-validation-report.md:26`、
  `workflow-v1-internal-engineering-closure-report.md:20,130`、
  `workflow-v1-release-baseline-manifest.md:44`：V2.6.16 SHA 数字段，7项，`FALSE_POSITIVE`。

真实手机号、邮箱、姓名或身份证命中为0。规则只识别显式 synthetic marker、固定测试
路径与完整64位Hash上下文，没有全局关闭 PII Scan。

## 6. Fixture Discovery

RC2-S10 中 Mapping Hard Gate 失败后，Secret/PII 与 Fixture Discovery 均被 Fail Closed
中止；原 `0/0/0` 是 `ABORTED_BY_HARD_GATE`，不是真实 Regression。

S10.1 使用全新隔离 MySQL 8.4.9（非34061、非RC1 datadir）依次执行唯一权威链：
Evidence Collector → Attestation Verifier → Fixture Contract → Application Seeder → Read-only
Discovery。Attestation PASS，Fixture Contract 16/16，Seeder 1/1。

只读结果：ROLE bound node 1、合法 Directory candidate userId 2、Recommended 1；
Proposed scope 为 enterprise 990001 / definition 990401 / version 990402 / node 990404 /
role `RC1_TEST_CANARY_APPROVER` / organization 990101，状态仅
`PROPOSED_NOT_ENABLED`。Runtime Instance、Task、CandidatePool、Claim、Admission 均为0。

## 7. 资产保护

- Migration SHA：45/45 PASS；V2.6.21/22/23未变化。
- V2.6.24：未创建。
- RC1 TEST 34061：未访问。
- ROLE Runtime：DISABLED。
- Canary：NOT_AUTHORIZED_NOT_ENABLED。
- Kill Switch：STOP_NEW_AND_CLAIM。
