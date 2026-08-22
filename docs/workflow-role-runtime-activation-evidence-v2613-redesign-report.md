# V2.6.13 Activation Evidence 候选重构治理报告

## 1. 失败版本治理说明

原 V2.6.13 从未晋级、从未在任何有效环境成功执行。WF5.10.1 的 MySQL 8.4.9
验收在创建 Approval 外键时以错误 `6125` 失败。

原文件已按原始字节归档到 Flyway 扫描目录之外：

`database/migration/archive/failed-candidates/V2.6.13__create_role_runtime_activation_evidence__failed_7ec6ec64.sql`

- 状态：`FAILED_CANDIDATE_ARCHIVED`
- SHA-256：`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`
- 原失败报告保持不变：`workflow-role-runtime-activation-evidence-v2613-validation-report.md`

未修改 Flyway history，未执行 repair、baseline、out-of-order，也未创建 V2.6.14。

## 2. 重构原因

原候选使用父键 `(activation_id, delete_token)`，但子外键仅引用 `activation_id`，
不满足 MySQL 外键父键要求。此外，Evidence 缺少具体 Approval 所有权，数据库无法证明
Evidence 的 Activation、Contract、Binding Hash 与批准事实一致。

本次在明确授权下重写未晋级候选，并同步 Domain、Entity 和 Mapper 契约，避免出现数据库
已加强而应用不能写入的半实现。

## 3. 数据库结构

仍只新增三张表：

| 表 | 字段数 | 核心职责 |
|---|---:|---|
| `role_runtime_activation_request` | 23 | Activation 稳定业务身份与最终请求事实 |
| `role_runtime_activation_approval` | 24 | 三方审批决策及三项批准 Hash 快照 |
| `role_runtime_activation_evidence` | 13 | 绑定具体 Approval 的分类 Hash 证据 |

合计：60 字段、16 索引、3 外键、17 CHECK、8 Trigger。

不保存用户目录、组织目录、候选成员、Task、Candidate Pool、Claim 或 Investment 数据。

## 4. 约束与外键设计

### Request → Approval

Approval 使用完整复合外键：

`(activation_id, delete_token) → request(activation_id, delete_token)`

父表对应明确唯一键 `uk_role_activation_owner`，关闭 MySQL 6125。

### Request/Approval → Evidence

Evidence 同时具备：

- `(activation_id, delete_token) → Request`；
- `(approval_id, activation_id, delete_token) → Approval`。

第二个复合外键保证 Approval ID、Activation ID 与逻辑删除身份属于同一所有权链，
Evidence 无法引用其他 Activation 的 Approval。

### Hash 身份

`activation_hash` 是内容指纹，不再作为全局唯一业务键。不同 Activation 可以拥有相同治理
内容；唯一身份由 `(activation_id, delete_token)` 管理。

## 5. Hash 与 Trigger 治理

Approval 新增并冻结：

- `activation_hash`
- `contract_hash`
- `binding_hash`

Evidence 新增同样三项 Hash 和 `approval_id`。

写入链路：

1. Approval INSERT Trigger 读取 Request，并校验三项 Hash 完全一致；
2. Evidence INSERT Trigger 读取具体 Approval，并校验三项 Hash 完全一致；
3. 任一不存在或不一致，MySQL `1644 / 45000` fail closed；
4. 所有 Hash 继续使用 `ASCII/ascii_bin` 和小写 SHA-256 CHECK。

Request、Approval、Evidence 均拒绝 UPDATE/DELETE；其中 Approval/Evidence append-only
由四个独立 Trigger 强制。

## 6. Guard 与失败安全

永久 DDL 前的 Temporary Guard 一次性检查：

- V2.6.12 两个 Resolver Version 字段均为 `ASCII/ascii_bin NOT NULL`；
- 三个目标表均不存在；
- 三个目标对象不存在残留 Trigger。

通过预建部分 Request 表模拟非法历史/失败残留后，Migration 在首个新永久 DDL 前失败：

- 新 Approval/Evidence 表：0；
- 新 Activation Trigger：0；
- 自动修复、删除、补数据：0。

说明：MySQL DDL 无法整体事务回滚；Guard 保证的是已识别的历史/部分安装异常在任何新 DDL
前阻断。未知的基础设施级 DDL 故障仍需发布 Runbook 与一次性 Schema 隔离保障。

## 7. 应用契约变化

- `PersistentActivationDecision` 增加 Activation/Contract/Binding Hash；
- `ActivationEvidenceRecord` 增加 `approvalId` 和三项 Hash；
- `ActivationPersistencePolicy` 将五类 Evidence 绑定到最终 Release Approver；
- Entity、Mapper、Repository 转换同步；
- Repository 仍只允许 insert/query；
- 未新增 Controller、Runtime Binding 或启用入口。

## 8. 测试结果

### MySQL/Flyway 候选预验证

环境：MySQL Community Server 8.4.9、Flyway 13.0.0，全新 loopback-only 临时实例。

| 验证项 | 结果 |
|---|---|
| Fresh：2.0.0 → 2.6.13 | PASS |
| Upgrade：2.6.12 → 2.6.13 | PASS，仅执行 V2.6.13 |
| Forward：2.6.8 → 2.6.9…2.6.13 | PASS |
| strict validate | PASS |
| 二次 migrate no-op | PASS |
| MySQL 6125 | 已关闭 |
| Evidence 无 Approval | 拒绝，1644 |
| Contract/Binding Hash 漂移 | 拒绝，1644 |
| Approval UPDATE/DELETE | 拒绝，1644 |
| Evidence UPDATE/DELETE | 拒绝，1644 |
| 非法 Hash、状态、delete_token | 拒绝，3819 |
| 事务内 Evidence 失败 | Request/Approval/Evidence 全部回滚 |
| Guard 失败 | 无新增永久表/Trigger |
| 双会话同 Activation | 1 成功、1 个 1062；无 1213/1205 |

首次并发调用使用 `Start-Process` 传递含分号 SQL 时被 MySQL 客户端解析为帮助请求，属于验收
脚本参数转义错误；改用两个 PowerShell Job 直接调用客户端后，独立双会话结果满足单赢家。
该问题未涉及候选 SQL 修改。

### Schema Fingerprint

Fresh、Upgrade、Forward 完全一致：

- Full：`f62b80c19ddf928bb9ed6db65c5792091dfbff56cc74d229c187927b3e5ffb1b`
- Workflow：`2b31cdbc9c7d9863893b2dc0d357c792cdfbc51185fce9961bd3602a354c1385`
- ROLE Runtime：`2dd606e66d8eca899f2cb96e60f649ba480ba1fe4df2f0c9909ebabf2a197573`

### 应用回归

- Java 21 编译：通过
- Spring Boot Context：通过
- 后端测试：434 通过，0 失败、0 错误、0 跳过
- Domain 纯净检查：通过
- `git diff --check`：通过
- Migration SHA 清单：35/35 匹配
- V2.6.14：不存在

## 9. 新 SHA 与资产状态

- 新 V2.6.13 SHA-256：
  `da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17`
- 预验证 Flyway checksum：`1882937893`
- 当前资产状态：`CANDIDATE_READY_FOR_VALIDATION`
- 执行状态：`EPHEMERAL_MYSQL8_PREVALIDATED`
- 未晋级 `CANONICAL_IMMUTABLE`
- `ROLE_RUNTIME_DISABLED`

## 10. 剩余风险

1. 本次是候选重构预验证，不替代独立的正式资产晋级 Sprint；
2. 三方 Approval 数量完整性仍由 Domain/Application 原子事务约束，数据库主要保障每条记录
   的所有权和 Hash 一致性；
3. MySQL DDL 非事务性要求正式验收继续使用一次性隔离 Schema；
4. Sandbox Evidence 只形成内存草稿，不能绕过 Approval 入库；
5. ROLE_DIRECTORY_V1 仍为 `PREPARED / NON_EXECUTABLE`。

最终状态：

- `V2.6.13 = CANDIDATE_READY_FOR_VALIDATION`
- `ROLE_RUNTIME_DISABLED`

本 Sprint 不进入 WF5.11、ROLE Task、Candidate Pool Runtime 或 Claim Runtime。
