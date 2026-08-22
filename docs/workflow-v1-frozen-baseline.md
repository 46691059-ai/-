# Workflow V1.0 Frozen Baseline

## 1. 冻结标识

- 审计工作区 HEAD：`f2429571c5255366b26553fe29064b6052faa437`
- Migration 正式链：V2.5.0—V2.6.16；共 23 个 Workflow Migration
- 全库 Migration SHA 清单：38/38 PASS
- 最新资产：V2.6.15 与 V2.6.16 均为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.17：不存在

工作区包含历次 Sprint 尚未提交的累计资产；本文件冻结的是经 SHA、Inventory、测试和结构扫描确认的内容基线，不代表已创建 Git release/tag。正式发布必须另行形成唯一可复现提交。

## 2. 架构与 Resolver

Workflow V1 采用 Domain → Application → Port → Infrastructure，REST 仅负责协议适配，不直接修改状态。Domain 不依赖 Spring、MyBatis、Entity、Mapper、Controller 或 Infrastructure。

`EXPLICIT_USER_V1` 是唯一 `ACTIVE` Resolver。`ROLE_DIRECTORY_V1` 为 `PREPARED / NON_EXECUTABLE`；不存在 ROLE→USER fallback。Resolver/Directory 失败一律 Fail Closed。

## 3. ROLE 治理链

ROLE 的冻结治理链为 Proposal → Eligibility → Activation Evidence → Promotion Candidate → Runtime Binding Snapshot → Admission → Realtime Eligibility Evidence → Claim Gate。当前只完成内部框架和持久化证据；真实 Directory、外部能力和生产控制源未验证，因此整条 ROLE Runtime 不可执行。

## 4. Canonical 与 Hash 原则

V1 冻结 `ROLE_DIRECTORY_PORT_V1`、`ROLE_CANONICAL_JSON_V1`、`ROLE_CANDIDATE_CANONICAL_V1`、`ROLE_BINDING_CANONICAL_V1`、`ROLE_RUNTIME_CANONICAL_V1`、`ROLE_RUNTIME_ACTIVATION_CANONICAL_V1`、`ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1`、`ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1`、`ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`、`ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`、`ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1`、`ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1`、`ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1` 和 `ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1`。

共同原则：固定字段顺序与显式 NULL/Boolean 语义、UTF-8、时间归一化、SHA-256 小写十六进制、Hash 字段使用二进制/大小写敏感校验；数据库 ID、审计时间等非业务字段按各 Contract 明确排除。禁止在 V1 内隐式升级 Canonical。

## 5. Runtime 默认状态

- Feature Flag：OFF
- Canary：无生产范围
- Kill Switch：`STOP_NEW_AND_CLAIM`
- ROLE Runtime：`DISABLED`
- Directory：无真实 TEST/PREPROD 验收资源

## 6. 不变量

1. Candidate Pool 在创建时冻结；人员变化不刷新或覆盖历史 Pool。
2. Claim 只校验冻结候选资格并复核实时执行资格，不重新调用 Directory 生成候选。
3. Directory Revision 与 `effectiveAt` 是证据的一部分；漂移必须阻断新执行。
4. Activation、Binding、Admission、Eligibility、Claim 与 Audit 事实 append-only。
5. 外部能力不可用、Hash/Contract/Revision 不一致时 Fail Closed。
6. 禁止 Resolver fallback、自动用户选择、自动升级/降级。
7. Legacy、USER + DIRECT、历史 Definition/Instance/Task/Pool/Claim 不回填、不重算、不重新解析。
8. Investment 负责业务语义、快照、风险与业务状态；Workflow 负责流程运行与任务治理，不直接修改 Investment。
9. Workflow 只读取外部 Directory/Capability；不维护组织、角色、人员或外部权限事实。

## 7. V2 变更原则

任何破坏性变更必须进入新的正式设计与版本流程，采用增量 Migration，提供 Canonical 版本决策、Legacy 策略、Fresh/Upgrade 验收、并发/回滚/幂等与外部能力证据。禁止修改本基线 Canonical Migration 或通过 repair/baseline 掩盖漂移。
